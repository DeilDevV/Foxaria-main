package com.foxaria.retention.listener;

import com.foxaria.retention.JdbcRetentionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class RetentionListener implements Listener {

    private final JdbcRetentionService retentionService;

    public RetentionListener(JdbcRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        retentionService.onJoin(event.getPlayer());
    }
}
