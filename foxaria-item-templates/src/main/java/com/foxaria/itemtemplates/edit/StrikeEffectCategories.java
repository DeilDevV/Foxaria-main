package com.foxaria.itemtemplates.edit;

import org.bukkit.potion.PotionEffectType;

import java.util.Set;

/**
 * Классификация эффектов без зависимости от PotionEffectCategory в API (1.21 Paper).
 */
public final class StrikeEffectCategories {

    private static final Set<String> BENEFICIAL_KEYS = Set.of(
        "minecraft:speed",
        "minecraft:haste",
        "minecraft:strength",
        "minecraft:instant_health",
        "minecraft:regeneration",
        "minecraft:resistance",
        "minecraft:fire_resistance",
        "minecraft:water_breathing",
        "minecraft:invisibility",
        "minecraft:night_vision",
        "minecraft:jump_boost",
        "minecraft:luck",
        "minecraft:slow_falling",
        "minecraft:conduit_power",
        "minecraft:dolphins_grace",
        "minecraft:absorption",
        "minecraft:hero_of_the_village",
        "minecraft:saturation",
        "minecraft:health_boost"
    );

    private static final Set<String> HARMFUL_KEYS = Set.of(
        "minecraft:slowness",
        "minecraft:instant_damage",
        "minecraft:poison",
        "minecraft:wither",
        "minecraft:weakness",
        "minecraft:mining_fatigue",
        "minecraft:hunger",
        "minecraft:nausea",
        "minecraft:blindness",
        "minecraft:darkness",
        "minecraft:levitation",
        "minecraft:unluck"
    );

    private StrikeEffectCategories() {
    }

    /** Бафф в слоте «на врага» — получит атакующий. */
    public static boolean misplacedBeneficialInVictimSlot(PotionEffectType type) {
        return BENEFICIAL_KEYS.contains(type.getKey().toString());
    }

    /** Дебафф в слоте «на себя» — получит цель удара. */
    public static boolean misplacedHarmfulInSelfSlot(PotionEffectType type) {
        return HARMFUL_KEYS.contains(type.getKey().toString());
    }

    public static boolean isBeneficial(PotionEffectType type) {
        return BENEFICIAL_KEYS.contains(type.getKey().toString());
    }
}
