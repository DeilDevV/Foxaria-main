package com.foxaria.regions.listener;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Три цикла «свечение 3 с → пауза 5 с»: Glowing + флаг сущности и на следующем тике
 * hide/show для зрителей в мире — так клиент владельца с кастомным scoreboard
 * обычно снова получает контур, как у остальных.
 */
public final class RegionIntruderGlow {

    private static final ConcurrentHashMap<UUID, AtomicInteger> GENERATION = new ConcurrentHashMap<>();

    private RegionIntruderGlow() {
    }

    public static void start(JavaPlugin plugin, Player player) {
        UUID id = player.getUniqueId();
        int gen = GENERATION.computeIfAbsent(id, u -> new AtomicInteger()).incrementAndGet();
        plugin.getServer().getScheduler().runTask(plugin, () -> runCycle(plugin, player, gen, 0));
    }

    public static void clear(Player player) {
        UUID id = player.getUniqueId();
        GENERATION.remove(id);
        stripGlow(player);
    }

    private static void runCycle(JavaPlugin plugin, Player player, int gen, int cycleIndex) {
        if (!player.isOnline()) {
            stripGlow(player);
            return;
        }
        if (generationStale(player.getUniqueId(), gen)) {
            stripGlow(player);
            return;
        }
        if (cycleIndex >= 3) {
            stripGlow(player);
            return;
        }
        applyGlow(plugin, player, gen);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || generationStale(player.getUniqueId(), gen)) {
                stripGlow(player);
                return;
            }
            stripGlow(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || generationStale(player.getUniqueId(), gen)) {
                    return;
                }
                runCycle(plugin, player, gen, cycleIndex + 1);
            }, 100L);
        }, 60L);
    }

    private static boolean generationStale(UUID id, int gen) {
        AtomicInteger cur = GENERATION.get(id);
        return cur == null || cur.get() != gen;
    }

    private static void applyGlow(JavaPlugin plugin, Player player, int gen) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true));
        player.setGlowing(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || generationStale(player.getUniqueId(), gen)) {
                return;
            }
            refreshEntityTrackers(plugin, player);
        });
    }

    private static void refreshEntityTrackers(JavaPlugin plugin, Player intruder) {
        for (Player viewer : intruder.getWorld().getPlayers()) {
            if (viewer.equals(intruder)) {
                continue;
            }
            viewer.hideEntity(plugin, intruder);
            viewer.showEntity(plugin, intruder);
        }
    }

    private static void stripGlow(Player player) {
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.setGlowing(false);
    }
}
