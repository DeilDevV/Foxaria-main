package com.foxaria.core.listener;

import com.foxaria.api.service.ConfigService;
import com.foxaria.core.service.FirstJoinTrackerService;
import com.foxaria.core.service.TeleportService;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;

import java.util.UUID;

/**
 * Ставит игрока в рандомную точку ещё ДО входа в мир (без общей стартовой точки).
 * Работает только для первого захода на backend.
 */
public final class FirstJoinSpawnLocationListener implements Listener {

    private final ConfigService configs;
    private final TeleportService teleport;
    private final FirstJoinTrackerService firstJoin;

    public FirstJoinSpawnLocationListener(ConfigService configs, TeleportService teleport, FirstJoinTrackerService firstJoin) {
        this.configs = configs;
        this.teleport = teleport;
        this.firstJoin = firstJoin;
    }

    /**
     * Paper-recommended event (doesn't create player early).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncSpawn(AsyncPlayerSpawnLocationEvent event) {
        if (!configs.main().getBoolean("rtp.auto-first-join.enabled", true)) {
            return;
        }
        UUID uuid = (event.getConnection() != null && event.getConnection().getProfile() != null)
            ? event.getConnection().getProfile().getId()
            : null;
        if (!firstJoin.isFirstJoin(uuid)) {
            return;
        }
        World world = event.getSpawnLocation() != null ? event.getSpawnLocation().getWorld() : null;
        if (world == null) {
            return;
        }
        // Immediate safe random spawn from prewarmed RTP pool (no sync chunk generation in event).
        teleport.consumePreparedRtpLocation(world).ifPresent(loc -> {
            event.setSpawnLocation(loc);
            firstJoin.markFirstJoinThisLogin(uuid); // keep title on join
            firstJoin.markSeen(uuid);
        });
    }

}

