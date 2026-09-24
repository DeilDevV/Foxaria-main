package com.foxaria.modernfurnace;

import com.foxaria.api.service.DatabaseGateway;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ModernFurnaceRepository {

    private final DatabaseGateway database;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public ModernFurnaceRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Optional<PersistedFurnaceJson>> load(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String wid = w.getUID().toString();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT data_json FROM fx_modern_furnace WHERE world = ? AND x = ? AND y = ? AND z = ?
                """, wid, x, y, z);
                 ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.<PersistedFurnaceJson>empty();
                }
                String json = rs.getString(1);
                return Optional.of(gson.fromJson(json, PersistedFurnaceJson.class));
            }
        });
    }

    public CompletableFuture<Void> save(Location loc, PersistedFurnaceJson data) {
        World w = loc.getWorld();
        if (w == null) {
            return CompletableFuture.completedFuture(null);
        }
        String wid = w.getUID().toString();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        long now = System.currentTimeMillis();
        String json = gson.toJson(data);
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_modern_furnace (world, x, y, z, data_json, updated_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE data_json = VALUES(data_json), updated_ms = VALUES(updated_ms)
                """
                : """
                INSERT INTO fx_modern_furnace (world, x, y, z, data_json, updated_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (world, x, y, z) DO UPDATE SET data_json = excluded.data_json, updated_ms = excluded.updated_ms
                """;
            try (var st = database.prepare(connection, sql, wid, x, y, z, json, now)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> delete(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return CompletableFuture.completedFuture(null);
        }
        String wid = w.getUID().toString();
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                DELETE FROM fx_modern_furnace WHERE world = ? AND x = ? AND y = ? AND z = ?
                """, wid, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                st.executeUpdate();
            }
        });
    }

    public String toJson(PersistedFurnaceJson j) {
        return gson.toJson(j);
    }

    public PersistedFurnaceJson fromJson(String s) {
        return gson.fromJson(s, PersistedFurnaceJson.class);
    }

    /** Загрузка всех печей в чанке (для кэша при ChunkLoad). */
    public CompletableFuture<Map<Location, PersistedFurnaceJson>> loadInChunk(World world, int chunkX, int chunkZ) {
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        String wid = world.getUID().toString();
        return database.query(connection -> {
            Map<Location, PersistedFurnaceJson> map = new HashMap<>();
            try (var st = database.prepare(connection, """
                SELECT x, y, z, data_json FROM fx_modern_furnace
                WHERE world = ? AND x >= ? AND x < ? AND z >= ? AND z < ?
                """, wid, x0, x0 + 16, z0, z0 + 16);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int x = rs.getInt(1);
                    int y = rs.getInt(2);
                    int z = rs.getInt(3);
                    String json = rs.getString(4);
                    Location loc = new Location(world, x, y, z);
                    map.put(loc, gson.fromJson(json, PersistedFurnaceJson.class));
                }
            }
            return map;
        });
    }
}
