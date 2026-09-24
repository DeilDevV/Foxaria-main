package com.foxaria.cases.service;

import com.foxaria.cases.model.CaseBlockItemConfig;
import com.foxaria.cases.model.CaseDefinition;
import com.foxaria.cases.model.CaseHologramConfig;
import com.foxaria.cases.model.CaseIdleEffectsConfig;
import com.foxaria.cases.model.CaseMenuConfig;
import com.foxaria.cases.model.CaseReward;
import com.foxaria.cases.model.RewardCommandGroup;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ConfigCaseService {

    private FileConfiguration config;
    private Map<String, CaseDefinition> cases = Map.of();

    public ConfigCaseService(FileConfiguration config) {
        reload(config);
    }

    public void reload(FileConfiguration config) {
        this.config = config;
        this.cases = loadCases(config.getConfigurationSection("cases"));
    }

    public String serverId() {
        return config.getString("settings.server-id", "default");
    }

    public double idleEffectRadius() {
        return config.getDouble("settings.idle-effect-radius", 2.5D);
    }

    public int animationDurationTicks() {
        return config.getInt("settings.animation-duration-ticks", 140);
    }

    public int rouletteItemsCount() {
        return config.getInt("settings.roulette-items-count", 12);
    }

    public double hologramLineSpacing() {
        return config.getDouble("settings.hologram-line-spacing", 0.28D);
    }

    public List<CaseDefinition> definitions() {
        return cases.values().stream().sorted(Comparator.comparing(CaseDefinition::id)).toList();
    }

    public Optional<CaseDefinition> definition(String caseId) {
        if (caseId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cases.get(caseId.toLowerCase(Locale.ROOT)));
    }

    public boolean isValidId(String caseId) {
        return caseId != null && caseId.matches("[a-z0-9][a-z0-9_-]{0,48}");
    }

    private Map<String, CaseDefinition> loadCases(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, CaseDefinition> loaded = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection caseSection = section.getConfigurationSection(id);
            if (caseSection == null) {
                continue;
            }
            loaded.put(id.toLowerCase(Locale.ROOT), loadDefinition(id.toLowerCase(Locale.ROOT), caseSection));
        }
        return Map.copyOf(loaded);
    }

    private CaseDefinition loadDefinition(String id, ConfigurationSection section) {
        ConfigurationSection blockSection = section.getConfigurationSection("block-item");
        Material blockMaterial = Material.matchMaterial(blockSection == null ? "ENDER_CHEST" : blockSection.getString("material", "ENDER_CHEST"));
        if (blockMaterial == null) {
            blockMaterial = Material.ENDER_CHEST;
        }
        CaseBlockItemConfig blockItem = new CaseBlockItemConfig(
            blockMaterial,
            blockSection == null ? "&6Кейс" : blockSection.getString("name", "&6Кейс"),
            blockSection == null ? List.of() : blockSection.getStringList("lore")
        );

        ConfigurationSection holoSection = section.getConfigurationSection("hologram");
        CaseHologramConfig hologram = new CaseHologramConfig(
            holoSection == null ? List.of("&6Кейс") : holoSection.getStringList("lines"),
            holoSection == null ? 1.35D : holoSection.getDouble("offset-y", 1.35D)
        );

        ConfigurationSection menuSection = section.getConfigurationSection("menu");
        CaseMenuConfig menu = new CaseMenuConfig(
            menuSection == null ? section.getString("display-name", id) : menuSection.getString("title", section.getString("display-name", id)),
            menuSection == null ? 22 : menuSection.getInt("open-button-slot", 22),
            menuSection == null ? 40 : menuSection.getInt("keys-hint-slot", 40),
            menuSection == null ? List.of() : menuSection.getStringList("buy-keys-lore")
        );

        ConfigurationSection idleSection = section.getConfigurationSection("idle-effects");
        Particle particle = Particle.END_ROD;
        if (idleSection != null) {
            Particle parsed = parseParticle(idleSection.getString("particle", "END_ROD"));
            if (parsed != null) {
                particle = parsed;
            }
        }
        CaseIdleEffectsConfig idleEffects = new CaseIdleEffectsConfig(
            idleSection == null ? "spiral" : idleSection.getString("type", "spiral"),
            particle,
            idleSection == null ? "#FFFFFF" : idleSection.getString("color", "#FFFFFF")
        );

        List<CaseReward> rewards = loadRewards(section.getConfigurationSection("rewards"));
        return new CaseDefinition(
            id,
            section.getString("display-name", id),
            blockItem,
            hologram,
            menu,
            idleEffects,
            rewards
        );
    }

    private List<CaseReward> loadRewards(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<CaseReward> rewards = new ArrayList<>();
        for (String rewardId : section.getKeys(false)) {
            ConfigurationSection rewardSection = section.getConfigurationSection(rewardId);
            if (rewardSection == null) {
                continue;
            }
            Material icon = Material.matchMaterial(rewardSection.getString("icon", "CHEST"));
            if (icon == null) {
                icon = Material.CHEST;
            }
            List<RewardCommandGroup> groups = new ArrayList<>();
            List<Map<?, ?>> commandMaps = rewardSection.getMapList("commands");
            for (Map<?, ?> map : commandMaps) {
                Object targetObj = map.get("target");
                Object runObj = map.get("run");
                if (targetObj == null || runObj == null) {
                    continue;
                }
                List<String> run = new ArrayList<>();
                if (runObj instanceof List<?> list) {
                    for (Object line : list) {
                        if (line != null) {
                            run.add(String.valueOf(line));
                        }
                    }
                } else {
                    run.add(String.valueOf(runObj));
                }
                groups.add(new RewardCommandGroup(String.valueOf(targetObj), List.copyOf(run)));
            }
            rewards.add(new CaseReward(
                rewardId.toLowerCase(Locale.ROOT),
                rewardSection.getString("display", rewardId),
                Math.max(1, rewardSection.getInt("weight", 1)),
                icon,
                rewardSection.getString("rarity", "common"),
                rewardSection.getStringList("menu-lore"),
                List.copyOf(groups)
            ));
        }
        return List.copyOf(rewards);
    }

    private Particle parseParticle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
