package com.foxaria.core.listener;

import com.foxaria.api.service.ConfigService;
import com.foxaria.core.service.TeleportService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * На анархии нет фиксированного спавна: после смерти возрождаем на /home (первый),
 * а если домов нет — RTP по тем же правилам безопасности.
 */
public final class RespawnHomeOrRtpListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final TeleportService teleport;

    public RespawnHomeOrRtpListener(JavaPlugin plugin, ConfigService configs, TeleportService teleport) {
        this.plugin = plugin;
        this.configs = configs;
        this.teleport = teleport;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!configs.main().getBoolean("rtp.respawn.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        // После respawn'а безопаснее телепортировать на следующий тик.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> teleport.respawnToHomeOrRandom(player), 1L);
    }
}

