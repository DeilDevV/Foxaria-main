package com.foxaria.itemtemplates.ability;

/**
 * Настроенная способность на конкретном предмете.
 *
 * @param ability   какая способность
 * @param chance    шанс срабатывания, 0..100
 * @param power     сила (смысл зависит от способности)
 * @param seconds   длительность в секундах
 */
public record AbilityInstance(ItemAbility ability, int chance, int power, int seconds) {

    public static AbilityInstance defaults(ItemAbility ability) {
        return new AbilityInstance(ability, ability.defaultChance(), ability.defaultPower(), ability.defaultSeconds());
    }

    public AbilityInstance withChance(int value) {
        return new AbilityInstance(ability, clamp(value, 1, 100), power, seconds);
    }

    public AbilityInstance withPower(int value) {
        return new AbilityInstance(ability, chance, clamp(value, 1, 50), seconds);
    }

    public AbilityInstance withSeconds(int value) {
        return new AbilityInstance(ability, chance, power, clamp(value, 1, 120));
    }

    public int durationTicks() {
        return Math.max(1, seconds) * 20;
    }

    /** Уровень эффекта для PotionEffect (там отсчёт с нуля). */
    public int amplifier() {
        return Math.max(0, power - 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
