package com.foxaria.ranks;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RankListener implements Listener {

    private final StandaloneRankService rankService;

    public RankListener(StandaloneRankService rankService) {
        this.rankService = rankService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rankService.refreshPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rankService.release(event.getPlayer());
    }
}
