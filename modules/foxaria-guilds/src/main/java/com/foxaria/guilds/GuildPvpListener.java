package com.foxaria.guilds;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class GuildPvpListener implements Listener {

    private final GuildService guilds;

    public GuildPvpListener(GuildService guilds) {
        this.guilds = guilds;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!guilds.isSameGuild(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (!guilds.isFriendlyFireEnabled(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
