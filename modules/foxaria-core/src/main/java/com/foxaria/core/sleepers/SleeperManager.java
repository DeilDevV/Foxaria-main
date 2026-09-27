package com.foxaria.core.sleepers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.java.JavaPlugin;

public class SleeperManager {

    private final JavaPlugin plugin;

    public SleeperManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnSleeper(Location loc, String playerName) {
        Zombie sleeper = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);

        // Не горит на солнце
        sleeper.setShouldBurnInDay(false);
        sleeper.setBaby(false);

        // Базовые настройки
        sleeper.setAI(false);
        sleeper.setSilent(true);
        sleeper.setRemoveWhenFarAway(false);
        sleeper.setCanPickupItems(false);

        // Ник
        sleeper.setCustomName("§7[Спящий] §f" + playerName);
        sleeper.setCustomNameVisible(true);

        // Убираем всю экипировку
        EntityEquipment equip = sleeper.getEquipment();
        if (equip != null) {
            equip.clear();
            equip.setHelmetDropChance(0);
            equip.setChestplateDropChance(0);
            equip.setLeggingsDropChance(0);
            equip.setBootsDropChance(0);
            equip.setItemInMainHandDropChance(0);
            equip.setItemInOffHandDropChance(0);

            // Голова со скином игрока
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                skull.setItemMeta(meta);
            }
            equip.setHelmet(skull);
        }

        // Зомби невидимый (видна только голова)
        sleeper.setInvisible(true);

        // Неуязвимость (чтобы мобы не убивали слипера)
        sleeper.setInvulnerable(true);

        // Сопротивление к огню
        sleeper.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        // Метаданные
        sleeper.setMetadata("foxaria:sleeper", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    }
}
