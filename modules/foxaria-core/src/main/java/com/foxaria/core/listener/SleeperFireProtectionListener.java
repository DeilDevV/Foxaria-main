package com.foxaria.core.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SleeperFireProtectionListener implements Listener {
    private final NamespacedKey sleeperOwnerKey;
    private final NamespacedKey sleeperKindKey;

    public SleeperFireProtectionListener(JavaPlugin plugin) {
        this.sleeperOwnerKey = new NamespacedKey(plugin, "sleeper-owner");
        this.sleeperKindKey  = new NamespacedKey(plugin, "sleeper-kind");
    }

    private boolean isSleeper(Entity entity) {
        if (entity == null) return false;
        String owner = entity.getPersistentDataContainer().get(sleeperOwnerKey, PersistentDataType.STRING);
        String kind  = entity.getPersistentDataContainer().get(sleeperKindKey,  PersistentDataType.STRING);
        return owner != null && !owner.isBlank() && "hitbox".equalsIgnoreCase(kind);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCombust(EntityCombustEvent event) {
        if (!isSleeper(event.getEntity())) return;
        event.setCancelled(true);
        event.getEntity().setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!isSleeper(event.getEntity())) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE
         || cause == EntityDamageEvent.DamageCause.FIRE_TICK
         || cause == EntityDamageEvent.DamageCause.LAVA
         || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            event.getEntity().setFireTicks(0);
        }
    }
}
