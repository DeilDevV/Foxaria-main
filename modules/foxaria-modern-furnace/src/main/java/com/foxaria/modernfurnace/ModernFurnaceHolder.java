package com.foxaria.modernfurnace;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ModernFurnaceHolder implements InventoryHolder {

    public enum Kind {
        MAIN,
        UPGRADES,
        PIPES
    }

    private final Location location;
    private final Kind kind;
    private Inventory inventory;

    public ModernFurnaceHolder(Location location, Kind kind) {
        this.location = location.clone();
        this.kind = kind;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    public Location location() {
        return location;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
