package com.foxaria.shop.progression;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProgressionLifecycleListener implements Listener {

    private final JavaPlugin plugin;
    private final ProgressionService progression;

    public ProgressionLifecycleListener(JavaPlugin plugin, ProgressionService progression) {
        this.plugin = plugin;
        this.progression = progression;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> progression.onJoin(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        progression.onQuit(event.getPlayer());
    }
}
