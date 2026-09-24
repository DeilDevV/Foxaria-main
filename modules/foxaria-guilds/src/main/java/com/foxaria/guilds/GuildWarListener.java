package com.foxaria.guilds;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class GuildWarListener implements Listener {

    private final GuildWarEngine wars;

    public GuildWarListener(GuildWarEngine wars) {
        this.wars = wars;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        wars.recordKill(killer, victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wars.handlePlayerQuit(event.getPlayer());
    }
}
