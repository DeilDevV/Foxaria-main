package com.foxaria.kits;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.model.KitContents;
import com.foxaria.api.model.KitDefinition;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.KitService;
import com.foxaria.api.service.MessageService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CompositeKitService implements KitService {

    private final FileConfiguration yamlConfig;
    private final JavaPlugin plugin;
    private final KitRepository claimRepository;
    private final KitDefinitionRepository definitionRepository;
    private final MessageService messages;
    private final AuditService audits;
    private final AtomicReference<List<KitDefinition>> databaseKits = new AtomicReference<>(List.of());

    public CompositeKitService(
        JavaPlugin plugin,
        ConfigService configs,
        KitRepository claimRepository,
        KitDefinitionRepository definitionRepository,
        MessageService messages,
        AuditService audits
    ) {
        this.yamlConfig = configs.module("modules/kits.yml");
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.definitionRepository = definitionRepository;
        this.messages = messages;
        this.audits = audits;
    }

    public void refreshDatabaseKits() {
        definitionRepository.listAll().thenAccept(list -> databaseKits.set(List.copyOf(list)));
    }

    @Override
    public List<KitDefinition> definitions() {
        Map<String, KitDefinition> byId = new LinkedHashMap<>();
        for (KitDefinition yamlKit : loadYamlKits()) {
            byId.put(yamlKit.id().toLowerCase(), yamlKit);
        }
        for (KitDefinition dbKit : databaseKits.get()) {
            byId.put(dbKit.id().toLowerCase(), dbKit);
        }
        return List.copyOf(byId.values());
    }

    private List<KitDefinition> loadYamlKits() {
        ConfigurationSection section = yamlConfig.getConfigurationSection("kits");
        if (section == null) {
            return List.of();
        }
        List<KitDefinition> definitions = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection kitSection = section.getConfigurationSection(key);
            if (kitSection == null) {
                continue;
            }
            KitContents contents = KitContents.fromFlatItems(readYamlItems(kitSection.getMapList("items")));
            definitions.add(new KitDefinition(
                key,
                kitSection.getString("display-name", key),
                kitSection.getLong("cooldown-seconds", 0L),
                kitSection.getString("permission", "foxaria.kits.use." + key),
                kitSection.getLong("required-playtime-seconds", 0L),
                contents
            ));
        }
        return definitions;
    }

    private List<ItemStack> readYamlItems(List<Map<?, ?>> rawItems) {
        List<ItemStack> items = new ArrayList<>();
        if (rawItems == null) {
            return items;
        }
        for (Map<?, ?> rawItem : rawItems) {
            Object materialRaw = rawItem.containsKey("material") ? rawItem.get("material") : "STONE";
            Material material = Material.matchMaterial(String.valueOf(materialRaw));
            if (material == null) {
                continue;
            }
            Object amountRaw = rawItem.containsKey("amount") ? rawItem.get("amount") : 1;
            int amount = Integer.parseInt(String.valueOf(amountRaw));
            items.add(new ItemStack(material, amount));
        }
        return items;
    }

    @Override
    public CompletableFuture<Void> claim(Player player, String kitId) {
        KitDefinition kit = definitions().stream()
            .filter(definition -> definition.id().equalsIgnoreCase(kitId))
            .findFirst()
            .orElse(null);
        if (kit == null) {
            messages.send(player, "kits.missing", "&cНабор не найден.");
            return CompletableFuture.completedFuture(null);
        }
        if (!player.hasPermission(kit.permission())) {
            messages.send(player, "general.no-permission", "&cУ вас нет доступа к этому набору.");
            return CompletableFuture.completedFuture(null);
        }
        long playtime = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L;
        if (playtime < kit.requiredPlaytimeSeconds()) {
            messages.send(player, "kits.playtime-required", "&cНужно больше наигранного времени для этого набора.");
            return CompletableFuture.completedFuture(null);
        }

        return claimRepository.claims(player.getUniqueId()).thenCompose(claims -> {
            long lastClaim = claims.getOrDefault(kit.id(), 0L);
            long cooldownEnd = lastClaim + (kit.cooldownSeconds() * 1000L);
            if (kit.cooldownSeconds() > 0 && cooldownEnd > System.currentTimeMillis()) {
                long seconds = Math.max(1L, (cooldownEnd - System.currentTimeMillis()) / 1000L);
                messages.send(player, "kits.cooldown", "&cНабор на перезарядке: &f<seconds> &cсек.",
                    new MessageService.Placeholder("seconds", String.valueOf(seconds)));
                return CompletableFuture.completedFuture(null);
            }

            return claimRepository.markClaimed(player.getUniqueId(), kit.id()).thenRun(() -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (ItemStack item : kit.contents().stacksForGive()) {
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
                        overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
                    }
                    messages.send(player, "kits.claimed", "&aВы получили набор: &f<kit>&a.",
                        new MessageService.Placeholder("kit", kit.displayName()));
                    audits.append(new AuditEvent(
                        "KIT_CLAIM",
                        player.getUniqueId(),
                        null,
                        player.getName(),
                        null,
                        "Kit claimed",
                        Map.of("kit", kit.id()),
                        System.currentTimeMillis()
                    ));
                });
            });
        });
    }
}
