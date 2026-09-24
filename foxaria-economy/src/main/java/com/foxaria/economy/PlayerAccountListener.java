package com.foxaria.economy;

import com.foxaria.api.service.EconomyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerAccountListener implements Listener {

    private final EconomyService economyService;

    public PlayerAccountListener(EconomyService economyService) {
        this.economyService = economyService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyService.ensureAccount(event.getPlayer());
    }
}
