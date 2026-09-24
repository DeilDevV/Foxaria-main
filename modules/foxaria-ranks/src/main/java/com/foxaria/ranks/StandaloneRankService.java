package com.foxaria.ranks;

import com.foxaria.api.service.PermissionService;
import com.foxaria.api.service.RankService;
import com.foxaria.core.service.FoxariaPermissionService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class StandaloneRankService implements RankService {

    private final JavaPlugin plugin;
    private final FoxariaRankRepository repository;
    private final FileConfiguration config;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<String, RankDefinition> definitions;
    private final String defaultGroup;
    private BukkitTask cleanupTask;

    public StandaloneRankService(JavaPlugin plugin, FoxariaRankRepository repository, FileConfiguration config) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        this.defaultGroup = normalize(config.getString("default-group", "default"));
        this.definitions = Collections.unmodifiableMap(loadDefinitions(config));
    }

    public void start() {
        refreshOnlinePlayers();
        long intervalTicks = Math.max(200L, config.getLong("cleanup-interval-ticks", 1200L));
        cleanupTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
            repository.cleanupExpired(System.currentTimeMillis()).thenRun(this::refreshOnlinePlayers), intervalTicks, intervalTicks);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        runSync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                release(player);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String group) {
        String resolvedGroup = resolveGroup(group);
        return repository.setPrimaryGroup(playerUuid, resolvedGroup).thenRun(() -> refreshPlayer(playerUuid));
    }

    @Override
    public CompletableFuture<Void> grantTemporaryGroup(UUID playerUuid, String group, long durationSeconds) {
        String resolvedGroup = resolveGroup(group);
        long expiresAt = durationSeconds <= 0 ? 0L : System.currentTimeMillis() + (durationSeconds * 1000L);
        return repository.grantTemporaryGroup(playerUuid, resolvedGroup, expiresAt).thenRun(() -> refreshPlayer(playerUuid));
    }

    @Override
    public CompletableFuture<Void> removeGroup(UUID playerUuid, String group) {
        return repository.removeGroup(playerUuid, resolveGroup(group), defaultGroup).thenRun(() -> refreshPlayer(playerUuid));
    }

    @Override
    public CompletableFuture<Void> grantTemporaryPermission(UUID playerUuid, String permission, long durationSeconds, String reason) {
        long expiresAt = durationSeconds <= 0 ? 0L : System.currentTimeMillis() + (durationSeconds * 1000L);
        return repository.grantTemporaryPermission(playerUuid, normalize(permission), expiresAt, reason).thenRun(() -> refreshPlayer(playerUuid));
    }

    @Override
    public CompletableFuture<String> primaryGroup(UUID playerUuid) {
        return repository.primaryGroup(playerUuid, defaultGroup);
    }

    public List<String> groups() {
        return new ArrayList<>(definitions.keySet());
    }

    public String displayName(String group) {
        RankDefinition definition = definitions.get(resolveGroup(group));
        return definition == null ? group : definition.displayName();
    }

    public void refreshPlayer(UUID playerUuid) {
        repository.snapshot(playerUuid, defaultGroup).thenAccept(snapshot -> runSync(() -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                applySnapshot(player, snapshot);
            }
        }));
    }

    public void release(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            player.removeAttachment(attachment);
        }
        player.displayName(serializer.deserialize(player.getName()));
        player.playerListName(serializer.deserialize(player.getName()));
    }

    private void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player.getUniqueId());
        }
    }

    private void applySnapshot(Player player, FoxariaRankRepository.RankSnapshot snapshot) {
        release(player);

        PermissionAttachment attachment = player.addAttachment(plugin);
        Set<String> permissions = new LinkedHashSet<>();
        permissions.addAll(resolvePermissions(snapshot.primaryGroup()));
        permissions.add("foxaria.rank." + normalize(snapshot.primaryGroup()));
        for (String temporaryGroup : snapshot.temporaryGroups()) {
            permissions.addAll(resolvePermissions(temporaryGroup));
            permissions.add("foxaria.rank." + normalize(temporaryGroup));
        }
        for (String permission : snapshot.temporaryPermissions()) {
            permissions.add(normalize(permission));
        }
        for (String permission : permissions) {
            attachment.setPermission(permission, true);
        }
        attachments.put(player.getUniqueId(), attachment);

        RankDefinition definition = definitions.getOrDefault(snapshot.primaryGroup(), definitions.get(defaultGroup));
        String prefix = definition == null ? "" : definition.prefix();
        player.displayName(serializer.deserialize(prefix + player.getName()));
        player.playerListName(serializer.deserialize(prefix + player.getName()));

        // Группа для чата/скорборда читается из proxy.sqlite (FoxariaPermissionService); пересобрать вложения после смены ранга
        refreshProxyBackedPermissions(player);
    }

    private void refreshProxyBackedPermissions(Player player) {
        var reg = plugin.getServer().getServicesManager().getRegistration(PermissionService.class);
        if (reg != null && reg.getProvider() instanceof FoxariaPermissionService fox) {
            fox.refreshGroupIfChanged(player);
        }
    }

    private Set<String> resolvePermissions(String group) {
        String normalizedGroup = resolveGroup(group);
        Set<String> resolved = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(normalizedGroup);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            RankDefinition definition = definitions.get(current);
            if (definition == null) {
                continue;
            }
            resolved.addAll(definition.permissions());
            for (String inherited : definition.inherits()) {
                if (!Objects.equals(current, inherited)) {
                    stack.push(resolveGroup(inherited));
                }
            }
        }
        return resolved;
    }

    private Map<String, RankDefinition> loadDefinitions(FileConfiguration configuration) {
        Map<String, RankDefinition> resolved = new LinkedHashMap<>(builtinDefinitions());
        ConfigurationSection groupsSection = configuration.getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String key : groupsSection.getKeys(false)) {
                ConfigurationSection groupSection = groupsSection.getConfigurationSection(key);
                if (groupSection == null) {
                    continue;
                }
                String groupId = normalize(key);
                RankDefinition builtin = resolved.getOrDefault(groupId, new RankDefinition(groupId, key, "", List.of(), Set.of()));
                resolved.put(groupId, new RankDefinition(
                    groupId,
                    groupSection.getString("display-name", builtin.displayName()),
                    groupSection.getString("prefix", builtin.prefix()),
                    normalizeCollection(groupSection.getStringList("inherits").isEmpty() ? builtin.inherits() : groupSection.getStringList("inherits")),
                    normalizeSet(groupSection.getStringList("permissions").isEmpty() ? builtin.permissions() : groupSection.getStringList("permissions"))
                ));
            }
            return resolved;
        }

        List<String> legacyGroups = configuration.getStringList("groups");
        if (!legacyGroups.isEmpty()) {
            Map<String, RankDefinition> legacyResolved = new LinkedHashMap<>();
            for (String value : legacyGroups) {
                String groupId = normalize(value);
                legacyResolved.put(groupId, resolved.getOrDefault(groupId, new RankDefinition(groupId, value, "", List.of(), Set.of())));
            }
            if (!legacyResolved.containsKey(defaultGroup) && resolved.containsKey(defaultGroup)) {
                legacyResolved.put(defaultGroup, resolved.get(defaultGroup));
            }
            return legacyResolved;
        }
        return resolved;
    }

    private Map<String, RankDefinition> builtinDefinitions() {
        Map<String, RankDefinition> builtins = new LinkedHashMap<>();
        builtins.put("default", new RankDefinition(
            "default",
            "Игрок",
            "&7Игрок &8| &f",
            List.of(),
            normalizeSet(Arrays.asList(
                "foxaria.spawn",
                "foxaria.rtp",
                "foxaria.menu",
                "foxaria.home",
                "foxaria.home.set",
                "foxaria.home.delete",
                "foxaria.home.list",
                "foxaria.tpa",
                "foxaria.tpaccept",
                "foxaria.tpdeny",
                "foxaria.tpahere",
                "foxaria.economy.balance",
                "foxaria.economy.balance.top",
                "foxaria.economy.pay",
                "foxaria.kits.use",
                "foxaria.shop.use",
                "foxaria.playershops.use",
                "foxaria.auction.use",
                "foxaria.customitems.use",
                "foxaria.rewards.use",
                "foxaria.streak.use",
                "foxaria.voteclaim.use",
                "foxaria.refer.use",
                "foxaria.season.use",
                "foxaria.report"
            ))
        ));
        builtins.put("supporter", new RankDefinition(
            "supporter",
            "Поддержка",
            "&aПоддержка &8| &f",
            List.of("default"),
            normalizeSet(List.of("foxaria.kits.use.daily"))
        ));
        builtins.put("vip", new RankDefinition(
            "vip",
            "VIP",
            "&6VIP &8| &f",
            List.of("supporter"),
            normalizeSet(List.of("foxaria.kits.use.vip"))
        ));
        builtins.put("elite", new RankDefinition(
            "elite",
            "Элита",
            "&bЭлита &8| &f",
            List.of("vip"),
            normalizeSet(List.of("foxaria.economy.balance.others"))
        ));
        builtins.put("helper", new RankDefinition(
            "helper",
            "Хелпер",
            "&2Хелпер &8| &f",
            List.of("default"),
            normalizeSet(Arrays.asList(
                "foxaria.mod.panel",
                "foxaria.mod.history",
                "foxaria.mod.note",
                "foxaria.mod.staffchat",
                "foxaria.mod.vanish.see"
            ))
        ));
        builtins.put("moderator", new RankDefinition(
            "moderator",
            "Модератор",
            "&3Модератор &8| &f",
            List.of("helper"),
            normalizeSet(Arrays.asList(
                "foxaria.mod.check",
                "foxaria.mod.warn",
                "foxaria.mod.mute",
                "foxaria.mod.tempmute",
                "foxaria.mod.kick",
                "foxaria.mod.ban",
                "foxaria.mod.tempban",
                "foxaria.mod.freeze",
                "foxaria.mod.socialspy",
                "foxaria.mod.commandspy",
                "foxaria.mod.invsee",
                "foxaria.mod.echest",
                "foxaria.mod.vanish",
                "foxaria.speed",
                "foxaria.playershops.inspect"
            ))
        ));
        builtins.put("admin", new RankDefinition(
            "admin",
            "Админ",
            "&cАдмин &8| &f",
            List.of("moderator"),
            normalizeSet(Arrays.asList(
                "foxaria.setspawn",
                "foxaria.economy.admin",
                "foxaria.ranks.manage",
                "foxaria.store.manage",
                "foxaria.admin.panel",
                "foxaria.admin.maintenance",
                "foxaria.admin.restart",
                "foxaria.admin.auditlog",
                "foxaria.security.admin",
                "foxaria.security.grimhook",
                "foxaria.customitems.admin"
            ))
        ));
        return builtins;
    }

    private List<String> normalizeCollection(Collection<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String resolved = normalize(value);
            if (!normalized.contains(resolved)) {
                normalized.add(resolved);
            }
        }
        return normalized;
    }

    private Set<String> normalizeSet(Collection<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(normalize(value));
        }
        return normalized;
    }

    private String resolveGroup(String group) {
        String normalized = normalize(group);
        return definitions.containsKey(normalized) ? normalized : defaultGroup;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? defaultGroup : value.toLowerCase(Locale.ROOT);
    }

    private void runSync(Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
