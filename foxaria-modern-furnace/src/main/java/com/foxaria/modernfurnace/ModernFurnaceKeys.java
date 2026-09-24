package com.foxaria.modernfurnace;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModernFurnaceKeys {

    public final NamespacedKey itemType;
    public final NamespacedKey serializedState;

    public ModernFurnaceKeys(JavaPlugin plugin) {
        this.itemType = new NamespacedKey(plugin, "mf_item");
        this.serializedState = new NamespacedKey(plugin, "mf_state");
    }
}
