package com.foxaria.regions;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionSession {

    private static final Map<UUID, Integer> LAST_REGION = new ConcurrentHashMap<>();

    private RegionSession() {
    }

    public static void setLastRegion(Player player, int regionId) {
        LAST_REGION.put(player.getUniqueId(), regionId);
    }

    public static int lastRegionId(Player player) {
        return LAST_REGION.getOrDefault(player.getUniqueId(), -1);
    }

    public static void forget(Player player) {
        LAST_REGION.remove(player.getUniqueId());
    }
}
