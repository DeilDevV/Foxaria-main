package com.foxaria.modernfurnace;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModernFurnacePending {

    public enum Mode {
        INPUT_CHEST,
        OUTPUT_CHEST
    }

    private final Map<UUID, Pending> map = new ConcurrentHashMap<>();

    public void set(UUID player, Location furnace, Mode mode) {
        map.put(player, new Pending(furnace.clone(), mode));
    }

    public Pending poll(UUID player) {
        return map.remove(player);
    }

    public Pending peek(UUID player) {
        return map.get(player);
    }

    public void clear(UUID player) {
        map.remove(player);
    }

    public record Pending(Location furnace, Mode mode) {
    }
}
