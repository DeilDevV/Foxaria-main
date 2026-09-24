package com.foxaria.regions.listener;

import com.foxaria.regions.RegionDamageService;
import com.foxaria.regions.RegionKeys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionExplosionListener implements Listener {

    private final JavaPlugin plugin;
    private final RegionDamageService damage;

    public RegionExplosionListener(JavaPlugin plugin, RegionDamageService damage) {
        this.plugin = plugin;
        this.damage = damage;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof TNTPrimed primed)) {
            return;
        }
        Integer tier = primed.getPersistentDataContainer().get(RegionKeys.dynamiteTier(plugin), PersistentDataType.INTEGER);
        if (tier == null || tier <= 0) {
            return;
        }
        event.blockList().clear();
        damage.applyExplosion(null, primed.getLocation(), tier);
    }
}
