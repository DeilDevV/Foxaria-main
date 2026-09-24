package com.foxaria.guilds.bridge;

import com.foxaria.guilds.GuildService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Периодически синхронизирует тег гильдии с Bungee (выход из гильдии, кик — без релога).
 */
public final class GuildProxyResyncTask implements Runnable {

    private final JavaPlugin plugin;
    private final GuildService guilds;
    private BukkitTask task;

    public GuildProxyResyncTask(JavaPlugin plugin, GuildService guilds) {
        this.plugin = plugin;
        this.guilds = guilds;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 40L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            guilds.guildOf(player.getUniqueId()).thenAccept(opt ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        ProxyGuildSync.send(plugin, guilds, player, opt);
                    }
                })
            );
        }
    }
}
