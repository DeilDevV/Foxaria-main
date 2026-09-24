package com.foxaria.core.service;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public final class LagProtectionService {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final AuditService audits;
    private BukkitTask cleanupTask;

    public LagProtectionService(JavaPlugin plugin, ConfigService configs, AuditService audits) {
        this.plugin = plugin;
        this.configs = configs;
        this.audits = audits;
    }

    public void start() {
        long interval = configs.main().getLong("lag-protection.cleanup-interval-ticks", 20L * 60L);
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanup, interval, interval);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }

    private void cleanup() {
        int removedItems = 0;
        int chunkLimit = configs.main().getInt("lag-protection.chunk-entity-limit", 180);
        int itemLifetimeTicks = configs.main().getInt("lag-protection.item-despawn-ticks", 20 * 120);

        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item && item.getTicksLived() > itemLifetimeTicks) {
                    item.remove();
                    removedItems++;
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                Entity[] entities = chunk.getEntities();
                if (entities.length <= chunkLimit) {
                    continue;
                }
                int toRemove = entities.length - chunkLimit;
                for (Entity entity : entities) {
                    if (toRemove <= 0) {
                        break;
                    }
                    if (entity instanceof Player) {
                        continue;
                    }
                    entity.remove();
                    toRemove--;
                }
            }
        }

        if (removedItems > 0) {
            audits.append(new AuditEvent(
                "ANTI_LAG_ENTITY_CLEANUP",
                null,
                null,
                "system",
                null,
                "Lag protection removed dropped items",
                Map.of("removedItems", String.valueOf(removedItems)),
                System.currentTimeMillis()
            ));
        }
    }
}
