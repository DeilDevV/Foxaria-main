package com.foxaria.regions;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionKeys {

    private RegionKeys() {
    }

    public static NamespacedKey privatItem(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "region-privat-item");
    }

    public static NamespacedKey dynamiteTier(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "region-dynamite-tier");
    }

    public static NamespacedKey blockRegionId(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "region-block-id");
    }

    /** Уровень привата, сохранённый на предмете ядра (после сноса). */
    public static NamespacedKey privatLevel(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "region-privat-level");
    }
}
