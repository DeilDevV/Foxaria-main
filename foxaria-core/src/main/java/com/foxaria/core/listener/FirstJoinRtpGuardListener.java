package com.foxaria.core.listener;

import com.foxaria.api.service.ConfigService;
import com.foxaria.core.service.FirstJoinTrackerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Не даёт игроку "обойти" первый RTP: блокируем движение до первого телепорта.
 *
 * Снимается, как только игрок сменил координаты (после RTP), или спустя таймаут.
 */
public final class FirstJoinRtpGuardListener implements Listener {

    private final ConfigService configs;
    private final FirstJoinTrackerService firstJoin;
    private final Set<UUID> locked = ConcurrentHashMap.newKeySet();

    public FirstJoinRtpGuardListener(ConfigService configs, FirstJoinTrackerService firstJoin) {
        this.configs = configs;
        this.firstJoin = firstJoin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!configs.main().getBoolean("rtp.auto-first-join.enabled", true)) {
            return;
        }
        if (firstJoin.isFirstJoin(player.getUniqueId())) {
            locked.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!locked.contains(player.getUniqueId())) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!locked.contains(player.getUniqueId())) {
            return;
        }
        // Любой плагин-телепорт на первый вход считаем завершением "рандомного спавна".
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
            || event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) {
            locked.remove(player.getUniqueId());
        }
    }

    public void unlock(Player player) {
        if (player != null) {
            locked.remove(player.getUniqueId());
        }
    }
}

