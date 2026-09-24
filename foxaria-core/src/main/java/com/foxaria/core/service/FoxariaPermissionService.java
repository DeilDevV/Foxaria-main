package com.foxaria.core.service;

import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.api.service.PermissionService;
import com.foxaria.api.service.ServiceRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

/**
 * Централизованные права Foxaria:
 * - источник прав: modules/ranks.yml (groups.*.permissions + inherits)
 * - для каждого игрока навешивается PermissionAttachment с итоговым набором прав его группы.
 *
 * LuckPerms и сторонние perm-плагины не используются.
 */
public final class FoxariaPermissionService implements PermissionService, Listener {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final DatabaseGateway database;

    private final Map<String, Set<String>> groupPermissions = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, String> cachedGroups = new HashMap<>();
    /** Префикс для чата/сайдбара с FoxariaProxy (Bungee), канал {@code ChatPrefixSync}. */
    private final ConcurrentHashMap<UUID, String> proxyChatPrefixLine = new ConcurrentHashMap<>();

    public FoxariaPermissionService(JavaPlugin plugin, ConfigService configs, ServiceRegistry services) {
        this.plugin = plugin;
        this.configs = configs;
        this.database = services.require(DatabaseGateway.class);

        reloadGroupPermissions();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void reloadGroupPermissions() {
        groupPermissions.clear();
        FileConfiguration cfg;
        try {
            cfg = configs.module("modules/ranks.yml");
        } catch (Exception ex) {
            plugin.getLogger().warning("Cannot load modules/ranks.yml for permissions, using empty config. " + ex.getMessage());
            return;
        }
        ConfigurationSection groupsRoot = cfg.getConfigurationSection("groups");
        if (groupsRoot == null) {
            return;
        }
        for (String groupId : groupsRoot.getKeys(false)) {
            resolvePermissionsForGroup(cfg, groupId, new HashSet<>());
        }
        plugin.getLogger().info("Loaded permission groups: " + groupPermissions.keySet());
    }

    private Set<String> resolvePermissionsForGroup(FileConfiguration cfg, String groupId, Set<String> visited) {
        if (groupPermissions.containsKey(groupId)) {
            return groupPermissions.get(groupId);
        }
        if (!visited.add(groupId)) {
            return Collections.emptySet();
        }
        ConfigurationSection groupsRoot = cfg.getConfigurationSection("groups");
        if (groupsRoot == null) {
            return Collections.emptySet();
        }
        ConfigurationSection groupSec = groupsRoot.getConfigurationSection(groupId);
        if (groupSec == null) {
            return Collections.emptySet();
        }
        Set<String> perms = new HashSet<>(groupSec.getStringList("permissions"));
        for (String parent : groupSec.getStringList("inherits")) {
            perms.addAll(resolvePermissionsForGroup(cfg, parent, visited));
        }
        groupPermissions.put(groupId, perms);
        return perms;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyPermissions(event.getPlayer());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> requestChatPrefixFromProxy(event.getPlayer()), 1L);
    }

    /** Запрос префикса у BungeeCord (ответ — {@code ChatPrefixSync}). */
    public void requestChatPrefixFromProxy(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("RequestChatPrefix");
        player.sendPluginMessage(plugin, "foxaria:proxy", out.toByteArray());
    }

    public void setProxyChatPrefix(UUID uuid, String legacyAmpersandPrefix) {
        if (uuid == null) {
            return;
        }
        if (legacyAmpersandPrefix == null || legacyAmpersandPrefix.isBlank()) {
            proxyChatPrefixLine.remove(uuid);
        } else {
            proxyChatPrefixLine.put(uuid, legacyAmpersandPrefix);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        proxyChatPrefixLine.remove(id);
        PermissionAttachment attachment = attachments.remove(id);
        if (attachment != null) {
            event.getPlayer().removeAttachment(attachment);
        }
    }

    private void applyPermissions(Player player) {
        UUID id = player.getUniqueId();
        PermissionAttachment old = attachments.remove(id);
        if (old != null) {
            player.removeAttachment(old);
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(id, attachment);

        String group = resolveGroupFromDatabase(id);
        cachedGroups.put(id, group);
        Set<String> perms = groupPermissions.getOrDefault(group, Collections.emptySet());
        for (String node : perms) {
            attachment.setPermission(node, true);
        }
    }

    /**
     * Перечитать группу из proxy.sqlite и обновить вложения (если группа изменилась).
     *
     * @return true если группа изменилась и права пересобраны
     */
    public boolean refreshGroupIfChanged(Player player) {
        String fresh = resolveGroupFromDatabase(player.getUniqueId());
        String prev = cachedGroups.get(player.getUniqueId());
        if (prev != null && prev.equals(fresh)) {
            return false;
        }
        applyPermissions(player);
        return true;
    }

    private String currentGroup(Player player) {
        return resolveGroupFromDatabase(player.getUniqueId());
    }

    /**
     * Группа из proxy БД (используется scoreboard и офлайн uuid).
     */
    public String resolveGroupFromDatabase(UUID playerUuid) {
        FileConfiguration cfg = safeRanksConfig();
        String fallback = cfg == null ? "default" : cfg.getString("default-group", "default");
        if (cfg == null || !cfg.getBoolean("proxy-bridge.enabled", true)) {
            return fallback;
        }
        String table = cfg.getString("proxy-bridge.table", "proxy_privileges");
        String sql = "SELECT primary_group, temp_group, temp_expires_at FROM " + table + " WHERE player_uuid=?";
        try {
            return database.query(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return fallback;
                    }
                    String primary = rs.getString("primary_group");
                    String temp = rs.getString("temp_group");
                    long expires = rs.getLong("temp_expires_at");
                    if (temp != null && !temp.isBlank() && expires > System.currentTimeMillis()) {
                        return temp;
                    }
                    return primary == null || primary.isBlank() ? fallback : primary;
                }
                }
            }).join();
        } catch (Exception ex) {
            plugin.getLogger().warning("Cannot read proxy privilege group: " + ex.getMessage());
            return fallback;
        }
    }

    private FileConfiguration safeRanksConfig() {
        try {
            return configs.module("modules/ranks.yml");
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public CompletableFuture<Void> grantTemporary(UUID playerUuid, String permission, long durationSeconds, String reason) {
        // В собственной системе прав временные node-ы пока не поддерживаем.
        // Оставляем заглушку, чтобы не ломать API.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String groupName) {
        // Центральная группа живёт в proxy БД; backend только читает.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> primaryGroup(Player player) {
        return CompletableFuture.completedFuture(currentGroup(player));
    }

    public List<String> debugPermissions(Player player) {
        String group = currentGroup(player);
        Set<String> perms = groupPermissions.getOrDefault(group, Collections.emptySet());
        return perms.stream().sorted().toList();
    }

    public String debugGroup(Player player) {
        return currentGroup(player);
    }

    /**
     * Отображаемое имя группы для scoreboard/чата.
     */
    public String rankDisplayName(String groupId) {
        FileConfiguration cfg = safeRanksConfig();
        if (cfg == null) {
            return groupId;
        }
        ConfigurationSection g = groupSection(cfg, groupId);
        if (g == null) {
            return groupId;
        }
        return g.getString("display-name", groupId);
    }

    /**
     * Цветной ярлык ранга для scoreboard: только строка с прокси (конфиг Bungee); иначе краткое имя группы.
     */
    public String rankLabelForSidebar(UUID playerUuid, String groupId) {
        String fromProxy = proxyChatPrefixLine.get(playerUuid);
        if (fromProxy != null && !fromProxy.isBlank()) {
            return fromProxy;
        }
        return "&7" + humanizeRankLabel(groupId);
    }

    /**
     * Префикс ранга для чата — только из FoxariaProxy ({@link #setProxyChatPrefix}); без локального cosmetic из YAML.
     */
    public String rankPrefixForChat(Player player) {
        String group = currentGroup(player);
        cachedGroups.put(player.getUniqueId(), group);
        String fromProxy = proxyChatPrefixLine.get(player.getUniqueId());
        return fromProxy != null && !fromProxy.isBlank() ? fromProxy : "";
    }

    private static String humanizeRankLabel(String group) {
        return switch (group == null ? "default" : group.toLowerCase(Locale.ROOT)) {
            case "supporter" -> "Поддержка";
            case "vip" -> "VIP";
            case "elite" -> "Элита";
            case "helper" -> "Хелпер";
            case "moderator" -> "Модератор";
            case "admin" -> "Админ";
            default -> "Игрок";
        };
    }

    private ConfigurationSection groupSection(FileConfiguration cfg, String groupId) {
        if (cfg == null || groupId == null || groupId.isBlank()) {
            return null;
        }
        ConfigurationSection byExact = cfg.getConfigurationSection("groups." + groupId);
        if (byExact != null) {
            return byExact;
        }
        return cfg.getConfigurationSection("groups." + groupId.toLowerCase());
    }
}
