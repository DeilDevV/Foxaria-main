package com.foxaria.guilds;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class GuildMobRewardListener implements Listener {

    private final GuildService service;

    public GuildMobRewardListener(GuildService service) {
        this.service = service;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        service.rewardForKill(killer, event.getEntityType().name());
    }
}
