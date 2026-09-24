package com.foxaria.regions.listener;

import com.foxaria.regions.RegionBlockMarkers;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionRecord;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * После рестарта мир подгружает чанки до того, как игрок взаимодействует с ядром:
 * типы блоков и PDC на коптильне могут «сброситься» при перегенерации/миграции чанка.
 * Восстанавливаем нижний {@link Material#SMITHING_TABLE} + верхний {@link Material#SMOKER} и маркеры PDC по данным из БД.
 */
public final class RegionCoreRestoreListener implements Listener {

    private final RegionFacade f;

    public RegionCoreRestoreListener(RegionFacade f) {
        this.f = f;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        f.plugin().getServer().getScheduler().runTaskLater(f.plugin(), () -> restoreWorld(event.getWorld()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        restoreChunk(event.getChunk());
    }

    private void restoreWorld(World world) {
        for (RegionRecord r : f.manager().snapshot()) {
            if (!r.world().equals(world.getName())) {
                continue;
            }
            restoreCoreBlocks(world, r);
        }
    }

    private void restoreChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX() << 4;
        int cz = chunk.getZ() << 4;
        int cxe = cx + 15;
        int cze = cz + 15;
        for (RegionRecord r : f.manager().snapshot()) {
            if (!r.world().equals(world.getName())) {
                continue;
            }
            int bx = r.cabinetX();
            int bz = r.cabinetZ();
            if (bx < cx || bx > cxe || bz < cz || bz > cze) {
                continue;
            }
            restoreCoreBlocks(world, r);
        }
    }

    private void restoreCoreBlocks(World world, RegionRecord r) {
        int x = r.cabinetX();
        int y = r.cabinetY();
        int z = r.cabinetZ();
        Block bottom = world.getBlockAt(x, y, z);
        Block top = world.getBlockAt(x, y + 1, z);
        if (bottom.getType() != Material.SMITHING_TABLE) {
            bottom.setType(Material.SMITHING_TABLE, false);
        }
        if (top.getType() != Material.SMOKER) {
            top.setType(Material.SMOKER, false);
        }
        RegionBlockMarkers.markCore(f.plugin(), bottom, r.id());
        RegionBlockMarkers.markCore(f.plugin(), top, r.id());
    }
}
