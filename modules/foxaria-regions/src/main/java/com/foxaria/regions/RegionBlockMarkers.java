package com.foxaria.regions;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class RegionBlockMarkers {

    private RegionBlockMarkers() {
    }

    public static void markCore(JavaPlugin plugin, Block block, int regionId) {
        BlockState st = block.getState();
        if (st instanceof TileState tile) {
            tile.getPersistentDataContainer().set(RegionKeys.blockRegionId(plugin), PersistentDataType.INTEGER, regionId);
            tile.update(true, false);
        }
    }

    public static Optional<Integer> regionIdOf(JavaPlugin plugin, Block block) {
        BlockState st = block.getState();
        if (st instanceof TileState tile) {
            Integer id = tile.getPersistentDataContainer().get(RegionKeys.blockRegionId(plugin), PersistentDataType.INTEGER);
            if (id != null) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public static void clear(JavaPlugin plugin, Block block) {
        BlockState st = block.getState();
        if (st instanceof TileState tile) {
            tile.getPersistentDataContainer().remove(RegionKeys.blockRegionId(plugin));
            tile.update(true, false);
        }
    }
}
