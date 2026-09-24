package com.foxaria.guilds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class GuildChestHolder implements InventoryHolder {

    private final String guildId;

    public GuildChestHolder(String guildId) {
        this.guildId = guildId;
    }

    public String guildId() {
        return guildId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("GuildChestHolder is marker-only.");
    }
}
