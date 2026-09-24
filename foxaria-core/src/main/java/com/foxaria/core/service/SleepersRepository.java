package com.foxaria.core.service;

import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SleepersRepository {

    private final DatabaseGateway db;

    public SleepersRepository(DatabaseGateway db) {
        this.db = db;
    }

    public void upsertSleeper(UUID playerUuid, String playerName, Location loc, UUID zombieUuid, UUID armorUuid, double health, String nameLineLegacy) {
        db.execute(connection -> {
            long now = System.currentTimeMillis();
            String sql = db.isMySql()
                ? "INSERT INTO fx_sleepers(player_uuid, player_name, world, x, y, z, yaw, pitch, created_at, entity_uuid, zombie_uuid, armor_uuid, health, name_line, state) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE') " +
                    "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), " +
                    "created_at=VALUES(created_at), entity_uuid=VALUES(entity_uuid), zombie_uuid=VALUES(zombie_uuid), armor_uuid=VALUES(armor_uuid), health=VALUES(health), name_line=VALUES(name_line), state='ACTIVE'"
                : "INSERT INTO fx_sleepers(player_uuid, player_name, world, x, y, z, yaw, pitch, created_at, entity_uuid, zombie_uuid, armor_uuid, health, name_line, state) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE') " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name, world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch, created_at=excluded.created_at, " +
                    "entity_uuid=excluded.entity_uuid, zombie_uuid=excluded.zombie_uuid, armor_uuid=excluded.armor_uuid, health=excluded.health, name_line=excluded.name_line, state='ACTIVE'";
            try (PreparedStatement st = db.prepare(connection,
                sql,
                playerUuid.toString(),
                playerName == null ? "" : playerName,
                loc.getWorld() == null ? "world" : loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                now,
                zombieUuid == null ? null : zombieUuid.toString(),
                zombieUuid == null ? null : zombieUuid.toString(),
                armorUuid == null ? null : armorUuid.toString(),
                health,
                nameLineLegacy == null ? "" : nameLineLegacy
            )) {
                st.executeUpdate();
            }
        });
    }

    public void clearItems(UUID playerUuid) {
        db.execute(connection -> {
            try (PreparedStatement del = db.prepare(connection, "DELETE FROM fx_sleeper_items WHERE player_uuid=?", playerUuid.toString())) {
                del.executeUpdate();
            }
        });
    }

    public void saveItems(UUID playerUuid, ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        db.execute(connection -> {
            try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO fx_sleeper_items(player_uuid, slot, item_base64) VALUES(?, ?, ?)")) {
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    ins.setString(1, playerUuid.toString());
                    ins.setInt(2, i);
                    ins.setString(3, ItemStackSerializer.serialize(item));
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        });
    }

    public Optional<SleeperRow> activeSleeper(UUID playerUuid) {
        return activeSleeperAsync(playerUuid).join();
    }

    public List<SleeperRow> activeSleepers() {
        return activeSleepersAsync().join();
    }

    public ItemStack[] loadItems(UUID playerUuid, int size) {
        return loadItemsAsync(playerUuid, size).join();
    }

    public void clearSlot(UUID playerUuid, int slot) {
        db.execute(connection -> {
            try (PreparedStatement st = db.prepare(connection,
                "DELETE FROM fx_sleeper_items WHERE player_uuid=? AND slot=?",
                playerUuid.toString(),
                slot)) {
                st.executeUpdate();
            }
        });
    }

    public Optional<SleeperRow> sleeperAny(UUID playerUuid) {
        return sleeperAnyAsync(playerUuid).join();
    }

    public CompletableFuture<Optional<SleeperRow>> activeSleeperAsync(UUID playerUuid) {
        return db.query(connection -> {
            try (PreparedStatement st = db.prepare(connection,
                "SELECT * FROM fx_sleepers WHERE player_uuid=? AND state='ACTIVE'",
                playerUuid.toString());
                 ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.<SleeperRow>empty();
                }
                return Optional.of(readSleeper(rs));
            }
        });
    }

    public CompletableFuture<List<SleeperRow>> activeSleepersAsync() {
        return db.query(connection -> {
            List<SleeperRow> out = new ArrayList<>();
            try (PreparedStatement st = db.prepare(connection, "SELECT * FROM fx_sleepers WHERE state='ACTIVE'");
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    out.add(readSleeper(rs));
                }
            }
            return out;
        });
    }

    public CompletableFuture<ItemStack[]> loadItemsAsync(UUID playerUuid, int size) {
        ItemStack[] out = new ItemStack[size];
        return db.query(connection -> {
            try (PreparedStatement st = db.prepare(connection,
                "SELECT slot, item_base64 FROM fx_sleeper_items WHERE player_uuid=?",
                playerUuid.toString());
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    String b64 = rs.getString("item_base64");
                    if (slot >= 0 && slot < out.length && b64 != null && !b64.isBlank()) {
                        out[slot] = ItemStackSerializer.deserialize(b64);
                    }
                }
            }
            return out;
        });
    }

    public CompletableFuture<Optional<SleeperRow>> sleeperAnyAsync(UUID playerUuid) {
        return db.query(connection -> {
            try (PreparedStatement st = db.prepare(connection,
                "SELECT * FROM fx_sleepers WHERE player_uuid=?",
                playerUuid.toString());
                 ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return Optional.<SleeperRow>empty();
                return Optional.of(readSleeper(rs));
            }
        });
    }

    public void markKilled(UUID playerUuid) {
        db.execute(connection -> {
            try (PreparedStatement st = db.prepare(connection,
                "UPDATE fx_sleepers SET state='KILLED' WHERE player_uuid=?",
                playerUuid.toString())) {
                st.executeUpdate();
            }
            try (PreparedStatement del = db.prepare(connection, "DELETE FROM fx_sleeper_items WHERE player_uuid=?", playerUuid.toString())) {
                del.executeUpdate();
            }
        });
    }

    public void markRestored(UUID playerUuid) {
        db.execute(connection -> {
            try (PreparedStatement st = db.prepare(connection,
                "UPDATE fx_sleepers SET state='RESTORED' WHERE player_uuid=?",
                playerUuid.toString())) {
                st.executeUpdate();
            }
            try (PreparedStatement del = db.prepare(connection, "DELETE FROM fx_sleeper_items WHERE player_uuid=?", playerUuid.toString())) {
                del.executeUpdate();
            }
        });
    }

    public void updateHealth(UUID playerUuid, double health) {
        db.execute(connection -> {
            try (PreparedStatement st = db.prepare(connection,
                "UPDATE fx_sleepers SET health=? WHERE player_uuid=? AND state='ACTIVE'",
                health,
                playerUuid.toString())) {
                st.executeUpdate();
            }
        });
    }

    private SleeperRow readSleeper(ResultSet rs) throws Exception {
        String world = rs.getString("world");
        double x = rs.getDouble("x");
        double y = rs.getDouble("y");
        double z = rs.getDouble("z");
        float yaw = (float) rs.getDouble("yaw");
        float pitch = (float) rs.getDouble("pitch");
        String pid = rs.getString("player_uuid");
        String name = rs.getString("player_name");
        String entity = rs.getString("entity_uuid");
        String zombie = safeColumn(rs, "zombie_uuid");
        String armor = safeColumn(rs, "armor_uuid");
        double health = safeDouble(rs, "health", 20.0);
        String nameLine = safeColumn(rs, "name_line");
        String state = safeColumn(rs, "state");
        long created = rs.getLong("created_at");
        return new SleeperRow(UUID.fromString(pid), name, world, x, y, z, yaw, pitch,
            entity == null || entity.isBlank() ? null : UUID.fromString(entity),
            zombie == null || zombie.isBlank() ? null : UUID.fromString(zombie),
            armor == null || armor.isBlank() ? null : UUID.fromString(armor),
            health,
            nameLine == null ? "" : nameLine,
            state == null ? "" : state,
            created);
    }

    private static String safeColumn(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double safeDouble(ResultSet rs, String col, double def) {
        try {
            double v = rs.getDouble(col);
            if (rs.wasNull()) return def;
            return v;
        } catch (Exception ignored) {
            return def;
        }
    }

    public record SleeperRow(
        UUID playerUuid,
        String playerName,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        UUID entityUuid,
        UUID zombieUuid,
        UUID armorUuid,
        double health,
        String nameLineLegacy,
        String state,
        long createdAt
    ) {
        public Location toLocation(World w) {
            return new Location(w, x, y, z, yaw, pitch);
        }
    }
}

