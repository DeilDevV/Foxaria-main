package com.foxaria.itemtemplates;

import org.bukkit.configuration.file.FileConfiguration;

public record ItemTemplateEditorConfig(
    int durabilityStep,
    double attackDamageStep,
    double attackSpeedStep,
    double attackKnockbackStep,
    double armorStep,
    double armorToughnessStep,
    double knockbackResistanceStep,
    boolean onHitEffectsEnabled,
    int potionDurationStepTicks,
    int potionExtendAllStepTicks
) {
    public static ItemTemplateEditorConfig from(FileConfiguration c) {
        boolean onHit = c.getBoolean("on-hit-effects.enabled", true);
        var s = c.getConfigurationSection("editor");
        if (s == null) {
            return new ItemTemplateEditorConfig(10, 0.5D, 0.05D, 0.05D, 1.0D, 0.5D, 0.05D, onHit, 20, 100);
        }
        return new ItemTemplateEditorConfig(
            Math.max(1, s.getInt("durability-step", 10)),
            s.getDouble("attack-damage-step", 0.5D),
            s.getDouble("attack-speed-step", 0.05D),
            s.getDouble("attack-knockback-step", 0.05D),
            s.getDouble("armor-step", 1.0D),
            s.getDouble("armor-toughness-step", 0.5D),
            s.getDouble("knockback-resistance-step", 0.05D),
            onHit,
            Math.max(1, s.getInt("potion-duration-step-ticks", 20)),
            Math.max(1, s.getInt("potion-extend-all-step-ticks", 100))
        );
    }

    public static ItemTemplateEditorConfig defaults() {
        return new ItemTemplateEditorConfig(10, 0.5D, 0.05D, 0.05D, 1.0D, 0.5D, 0.05D, true, 20, 100);
    }
}
