package com.foxaria.guilds.bridge;

import com.foxaria.guilds.GuildService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * После входа на игровой сервер отправляет тег гильдии на прокси (таб / имя).
 */
public final class GuildProxyBridgeListener implements Listener {

    private final JavaPlugin plugin;
    private final GuildService guilds;

    public GuildProxyBridgeListener(JavaPlugin plugin, GuildService guilds) {
        this.plugin = plugin;
        this.guilds = guilds;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            guilds.guildOf(player.getUniqueId()).thenAccept(opt ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        ProxyGuildSync.send(plugin, guilds, player, opt);
                    }
                })
            );
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ProxyGuildSync.sendClear(plugin, event.getPlayer());
    }
}
