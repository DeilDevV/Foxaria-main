package com.foxaria.shop.progression;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Блоки, поставленные игроком, не дают прогресс квеста «сломать блок» при повторном ломании.
 */
public final class PlayerPlacedBlockTracker {

    private final Map<String, Boolean> placed = new ConcurrentHashMap<>();

    private static String key(Location loc) {
        UUID w = loc.getWorld().getUID();
        return w + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public void markPlaced(Location loc) {
        placed.put(key(loc), Boolean.TRUE);
    }

    /** @return true если блок был отмечен как поставленный игроком (и снимается метка). */
    public boolean pollPlayerPlacedAndClear(Location loc) {
        return placed.remove(key(loc)) != null;
    }
}
