package com.foxaria.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record MenuHolder(BaseMenu menu) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return menu.inventory();
    }
}
