package com.foxaria.guilds;

import com.foxaria.guilds.gui.GuildChestHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class GuildChestListener implements Listener {

    private final GuildService service;

    public GuildChestListener(GuildService service) {
        this.service = service;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuildChestHolder holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            service.onGuildChestClosed(player, holder.guildId(), event.getInventory());
        }
        service.saveChest(holder.guildId(), event.getInventory());
    }
}
