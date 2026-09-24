package com.foxaria.core.service;

import com.foxaria.api.service.DatabaseGateway;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CoreRepository {

    private final DatabaseGateway database;
    private final JavaPlugin plugin;
    private final ConcurrentMap<UUID, List<String>> homeCache = new ConcurrentHashMap<>();

    public CoreRepository(DatabaseGateway database, JavaPlugin plugin) {
        this.database = database;
        this.plugin = plugin;
    }

    public void upsertPlayer(Player player) {
        database.execute(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_players SET name = ?, last_seen_at = ?, playtime_seconds = ? WHERE uuid = ?",
                player.getName(),
                now,
                player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L,
                player.getUniqueId().toString()
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_players (uuid, name, first_join_at, last_seen_at, playtime_seconds) VALUES (?, ?, ?, ?, ?)",
                player.getUniqueId().toString(),
                player.getName(),
                now,
                now,
                player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> saveSpawn(Location location) {
        return database.execute(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_spawn_points SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, updated_at = ? WHERE spawn_key = 'default'",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                now
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_spawn_points (spawn_key, world, x, y, z, yaw, pitch, updated_at) VALUES ('default', ?, ?, ?, ?, ?, ?, ?)",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                now
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<Location>> loadSpawn() {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(connection, "SELECT * FROM fx_spawn_points WHERE spawn_key = 'default'");
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readLocation(resultSet));
            }
        });
    }

    public CompletableFuture<Void> saveHome(UUID playerUuid, String homeName, Location location) {
        return database.execute(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_player_homes SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, created_at = ? WHERE player_uuid = ? AND home_name = ?",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                now,
                playerUuid.toString(),
                homeName
            )) {
                if (update.executeUpdate() == 0) {
                    try (PreparedStatement insert = database.prepare(
                        connection,
                        "INSERT INTO fx_player_homes (player_uuid, home_name, world, x, y, z, yaw, pitch, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        playerUuid.toString(),
                        homeName,
                        location.getWorld().getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch(),
                        now
                    )) {
                        insert.executeUpdate();
                    }
                }
            }
            homeCache.remove(playerUuid);
        });
    }

    public CompletableFuture<Optional<Location>> loadHome(UUID playerUuid, String homeName) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_player_homes WHERE player_uuid = ? AND home_name = ?",
                playerUuid.toString(),
                homeName
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readLocation(resultSet));
            }
        });
    }

    public CompletableFuture<List<String>> listHomes(UUID playerUuid) {
        List<String> cached = homeCache.get(playerUuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.query(connection -> {
            List<String> homes = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT home_name FROM fx_player_homes WHERE player_uuid = ? ORDER BY home_name ASC",
                playerUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    homes.add(resultSet.getString("home_name"));
                }
            }
            homeCache.put(playerUuid, homes);
            return homes;
        });
    }

    public CompletableFuture<Void> deleteHome(UUID playerUuid, String homeName) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "DELETE FROM fx_player_homes WHERE player_uuid = ? AND home_name = ?",
                playerUuid.toString(),
                homeName
            )) {
                statement.executeUpdate();
            }
            homeCache.remove(playerUuid);
        });
    }

    public CompletableFuture<Void> saveEntryPoint(UUID playerUuid, Location location) {
        return database.execute(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_player_entry_points SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, updated_at = ? WHERE player_uuid = ?",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                now,
                playerUuid.toString()
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_player_entry_points (player_uuid, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                playerUuid.toString(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                now
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<Location>> loadEntryPoint(UUID playerUuid) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_player_entry_points WHERE player_uuid = ?",
                playerUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readLocation(resultSet));
            }
        });
    }

    private Location readLocation(ResultSet resultSet) throws Exception {
        World world = Bukkit.getWorld(resultSet.getString("world"));
        if (world == null) {
            world = plugin.getServer().getWorlds().get(0);
        }
        return new Location(
            world,
            resultSet.getDouble("x"),
            resultSet.getDouble("y"),
            resultSet.getDouble("z"),
            resultSet.getFloat("yaw"),
            resultSet.getFloat("pitch")
        );
    }
}
