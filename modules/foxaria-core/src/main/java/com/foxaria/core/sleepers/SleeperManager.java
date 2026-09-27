package com.foxaria.core.sleepers;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

public class SleeperManager {

    private final JavaPlugin plugin;

    public SleeperManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnSleeper(Location loc, String playerName) {
        LivingEntity sleeper = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        
        if (sleeper instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }
        
        sleeper.removePotionEffect(PotionEffectType.INVISIBILITY);
        sleeper.setInvisible(false);
        
        sleeper.setAI(false);
        sleeper.setSilent(true);
        sleeper.setCustomName("§7[Спящий] §f" + playerName);
        sleeper.setCustomNameVisible(true);
        sleeper.setRemoveWhenFarAway(false);
        
        sleeper.setMetadata("foxaria:sleeper", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    }
}