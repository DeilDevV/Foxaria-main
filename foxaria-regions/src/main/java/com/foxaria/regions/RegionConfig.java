package com.foxaria.regions;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RegionConfig {

    public final int coreMaxHp;
    public final int coreWeaponDamage;
    public final double minAttackCooldown;
    public final int regionBlocksDown;
    public final int regionBlocksUp;
    public final int craftLogsRequired;
    public final int craftIronRequired;
    public final boolean craftWoodMustMatchType;
    public final long regenIdleBeforeMs;
    public final int regenIntervalTicks;
    public final int regenAmountPerTick;
    public final Map<Integer, Integer> dynamiteDamageByTier;
    public final int explosionXzRadius;
    public final int explosionYHalf;
    public final int defaultBlockHp;
    public final int bedrockHp;
    public final Map<Material, Integer> blockHpByMaterial;
    public final Map<Integer, LevelDef> levelsByNumber;
    public final ConfigurationSection raidManualSection;
    public final BigDecimal renameCostCoins;
    public final int renameNameMinLen;
    public final int renameNameMaxLen;
    public final int intrusionAlertMinLevel;
    public final int intrusionGlowMinLevel;
    public final long dismantleCooldownMs;
    public final long boundaryParticlePeriodTicks;

    public record LevelDef(int halfSizeBlocks, Optional<UpgradeCost> upgradeToNext) {}

    public record UpgradeCost(BigDecimal coins, int wood, int iron) {}

    private RegionConfig(
        int coreMaxHp,
        int coreWeaponDamage,
        double minAttackCooldown,
        int regionBlocksDown,
        int regionBlocksUp,
        int craftLogsRequired,
        int craftIronRequired,
        boolean craftWoodMustMatchType,
        long regenIdleBeforeMs,
        int regenIntervalTicks,
        int regenAmountPerTick,
        Map<Integer, Integer> dynamiteDamageByTier,
        int explosionXzRadius,
        int explosionYHalf,
        int defaultBlockHp,
        int bedrockHp,
        Map<Material, Integer> blockHpByMaterial,
        Map<Integer, LevelDef> levelsByNumber,
        ConfigurationSection raidManualSection,
        BigDecimal renameCostCoins,
        int renameNameMinLen,
        int renameNameMaxLen,
        int intrusionAlertMinLevel,
        int intrusionGlowMinLevel,
        long dismantleCooldownMs,
        long boundaryParticlePeriodTicks
    ) {
        this.coreMaxHp = coreMaxHp;
        this.coreWeaponDamage = coreWeaponDamage;
        this.minAttackCooldown = minAttackCooldown;
        this.regionBlocksDown = regionBlocksDown;
        this.regionBlocksUp = regionBlocksUp;
        this.craftLogsRequired = craftLogsRequired;
        this.craftIronRequired = craftIronRequired;
        this.craftWoodMustMatchType = craftWoodMustMatchType;
        this.regenIdleBeforeMs = regenIdleBeforeMs;
        this.regenIntervalTicks = regenIntervalTicks;
        this.regenAmountPerTick = regenAmountPerTick;
        this.dynamiteDamageByTier = dynamiteDamageByTier;
        this.explosionXzRadius = explosionXzRadius;
        this.explosionYHalf = explosionYHalf;
        this.defaultBlockHp = defaultBlockHp;
        this.bedrockHp = bedrockHp;
        this.blockHpByMaterial = blockHpByMaterial;
        this.levelsByNumber = levelsByNumber;
        this.raidManualSection = raidManualSection;
        this.renameCostCoins = renameCostCoins;
        this.renameNameMinLen = renameNameMinLen;
        this.renameNameMaxLen = renameNameMaxLen;
        this.intrusionAlertMinLevel = intrusionAlertMinLevel;
        this.intrusionGlowMinLevel = intrusionGlowMinLevel;
        this.dismantleCooldownMs = dismantleCooldownMs;
        this.boundaryParticlePeriodTicks = boundaryParticlePeriodTicks;
    }

    public static RegionConfig from(FileConfiguration c) {
        Map<Integer, Integer> dyn = new HashMap<>();
        ConfigurationSection dynSec = c.getConfigurationSection("dynamite.tiers");
        if (dynSec != null) {
            for (String k : dynSec.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(k);
                    ConfigurationSection sub = dynSec.getConfigurationSection(k);
                    int dmg = sub != null ? sub.getInt("block-damage", 25) : dynSec.getInt(k, 25);
                    dyn.put(tier, dmg);
                } catch (NumberFormatException ignored) {
                    // ignore bad keys
                }
            }
        }

        ConfigurationSection hpSec = c.getConfigurationSection("block-hp.values");
        Map<Material, Integer> matHp = new HashMap<>();
        if (hpSec != null) {
            for (String name : hpSec.getKeys(false)) {
                try {
                    matHp.put(Material.valueOf(name.toUpperCase()), hpSec.getInt(name));
                } catch (IllegalArgumentException ignored) {
                    // skip unknown material in config
                }
            }
        }

        ConfigurationSection lvlSec = c.getConfigurationSection("levels");
        Map<Integer, LevelDef> levels = new HashMap<>();
        if (lvlSec != null) {
            for (String k : lvlSec.getKeys(false)) {
                int lv = Integer.parseInt(k);
                int half = lvlSec.getInt(k + ".half-size-blocks", 25000);
                ConfigurationSection up = lvlSec.getConfigurationSection(k + ".upgrade");
                Optional<UpgradeCost> uc = Optional.empty();
                if (up != null) {
                    uc = Optional.of(new UpgradeCost(
                        BigDecimal.valueOf(up.getDouble("coins", 0)),
                        up.getInt("wood", 0),
                        up.getInt("iron", 0)
                    ));
                }
                levels.put(lv, new LevelDef(half, uc));
            }
        }

        int dismantleMin = Math.max(1, c.getInt("privat.dismantle-cooldown-minutes", 30));

        return new RegionConfig(
            c.getInt("core.max-hp", 100),
            c.getInt("core.weapon-damage", 5),
            c.getDouble("core.min-attack-cooldown", 0.99),
            c.getInt("region-bounds.blocks-down-from-core-base", 100),
            c.getInt("region-bounds.blocks-up-from-core-base", 100),
            c.getInt("cabinet-craft.logs-required", 32),
            c.getInt("cabinet-craft.iron-ingots-required", 16),
            c.getBoolean("cabinet-craft.wood-must-match-type", true),
            c.getLong("regen.idle-before-ms", 300_000L),
            c.getInt("regen.tick-interval-ticks", 200),
            c.getInt("regen.amount-per-tick", 10),
            Collections.unmodifiableMap(new HashMap<>(dyn)),
            c.getInt("dynamite.explosion-pattern.xz-radius", 1),
            c.getInt("dynamite.explosion-pattern.vertical-half-height", 0),
            c.getInt("block-hp.default-hp", 120),
            c.getInt("block-hp.bedrock-hp", 0),
            Collections.unmodifiableMap(matHp),
            Collections.unmodifiableMap(levels),
            c.getConfigurationSection("raid-manual"),
            BigDecimal.valueOf(c.getDouble("privat.rename-cost-coins", 10_000)),
            Math.max(1, c.getInt("privat.rename-name-min-len", 2)),
            Math.max(2, c.getInt("privat.rename-name-max-len", 32)),
            Math.max(1, c.getInt("privat.intrusion-alert-min-level", 2)),
            Math.max(1, c.getInt("privat.intrusion-glow-min-level", 3)),
            dismantleMin * 60_000L,
            Math.max(1L, c.getLong("privat.boundary-particle-period-ticks", 15L))
        );
    }

    public int blockHp(Material mat) {
        if (mat == Material.BEDROCK) {
            return bedrockHp;
        }
        return blockHpByMaterial.getOrDefault(mat, defaultBlockHp);
    }

    public LevelDef level(int n) {
        return levelsByNumber.getOrDefault(n, new LevelDef(25000, Optional.empty()));
    }

    public int dynamiteDamage(int tier) {
        return dynamiteDamageByTier.getOrDefault(tier, 25);
    }
}
