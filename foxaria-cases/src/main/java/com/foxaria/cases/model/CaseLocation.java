package com.foxaria.cases.model;

import org.bukkit.block.Block;

public record CaseLocation(String serverId, String world, int x, int y, int z) {

    public static CaseLocation from(Block block, String serverId) {
        return new CaseLocation(serverId, block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public String key() {
        return serverId + ":" + world + ":" + x + ":" + y + ":" + z;
    }
}
