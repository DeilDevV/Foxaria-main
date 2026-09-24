package com.foxaria.regions;

import com.foxaria.api.service.DatabaseGateway;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RegionRepository {

    private final DatabaseGateway database;

    public RegionRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<List<RegionRecord>> loadAllRegions() {
        return database.query(connection -> {
            List<RegionRecord> out = new ArrayList<>();
            try (var st = database.prepare(connection, "SELECT * FROM fx_regions");
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    out.add(readRegion(rs));
                }
            }
            return out;
        });
    }

    public CompletableFuture<Optional<RegionRecord>> findById(int id) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, "SELECT * FROM fx_regions WHERE id = ?", id);
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readRegion(rs));
                }
            }
            return Optional.<RegionRecord>empty();
        });
    }

    private static RegionRecord readRegion(ResultSet rs) throws Exception {
        String dn = rs.getString("display_name");
        if (rs.wasNull()) {
            dn = null;
        }
        return new RegionRecord(
            rs.getInt("id"),
            rs.getString("world"),
            rs.getInt("center_x"),
            rs.getInt("center_z"),
            rs.getInt("half_size"),
            rs.getInt("level"),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getInt("cabinet_x"),
            rs.getInt("cabinet_y"),
            rs.getInt("cabinet_z"),
            rs.getInt("core_hp"),
            rs.getInt("core_max_hp"),
            rs.getLong("core_last_damage_ms"),
            rs.getInt("deposited_wood"),
            rs.getInt("deposited_iron"),
            rs.getInt("flags"),
            dn
        );
    }

    public CompletableFuture<Integer> insertRegion(
        String world,
        int centerX,
        int centerZ,
        int halfSize,
        int level,
        UUID owner,
        int cx,
        int cy,
        int cz,
        int coreMaxHp
    ) {
        return database.query(connection -> {
            long now = System.currentTimeMillis();
            try (var st = connection.prepareStatement("""
                INSERT INTO fx_regions (
                  world, center_x, center_z, half_size, level, owner_uuid,
                  cabinet_x, cabinet_y, cabinet_z,
                  core_hp, core_max_hp, core_last_damage_ms,
                  deposited_wood, deposited_iron, flags
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 3)
                """, Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1, world);
                st.setInt(2, centerX);
                st.setInt(3, centerZ);
                st.setInt(4, halfSize);
                st.setInt(5, level);
                st.setString(6, owner.toString());
                st.setInt(7, cx);
                st.setInt(8, cy);
                st.setInt(9, cz);
                st.setInt(10, coreMaxHp);
                st.setInt(11, coreMaxHp);
                st.setLong(12, now);
                st.executeUpdate();
                try (ResultSet rs = st.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            try (var st = database.prepare(connection, "SELECT id FROM fx_regions WHERE owner_uuid = ? ORDER BY id DESC LIMIT 1", owner.toString());
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            throw new IllegalStateException("insert region failed");
        });
    }

    public CompletableFuture<Void> addMember(int regionId, UUID uuid, String role) {
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_region_members (region_id, member_uuid, role)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE role = VALUES(role)
                """
                : """
                INSERT OR REPLACE INTO fx_region_members (region_id, member_uuid, role) VALUES (?, ?, ?)
                """;
            try (var st = database.prepare(connection, sql, regionId, uuid.toString(), role)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> isMember(int regionId, UUID uuid) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT 1 FROM fx_region_members WHERE region_id = ? AND member_uuid = ?
                """, regionId, uuid.toString());
                 ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        });
    }

    public CompletableFuture<Void> removeMember(int regionId, UUID uuid) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                DELETE FROM fx_region_member_prefs WHERE region_id = ? AND member_uuid = ?
                """, regionId, uuid.toString())) {
                st.executeUpdate();
            }
            try (var st = database.prepare(connection, """
                DELETE FROM fx_region_members WHERE region_id = ? AND member_uuid = ?
                """, regionId, uuid.toString())) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateCore(int regionId, int hp, long lastDamageMs) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                UPDATE fx_regions SET core_hp = ?, core_last_damage_ms = ? WHERE id = ?
                """, hp, lastDamageMs, regionId)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateDeposits(int regionId, int wood, int iron) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                UPDATE fx_regions SET deposited_wood = ?, deposited_iron = ? WHERE id = ?
                """, wood, iron, regionId)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> upgradeRegion(int regionId, int newHalfSize, int newLevel) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                UPDATE fx_regions SET half_size = ?, level = ?, deposited_wood = 0, deposited_iron = 0 WHERE id = ?
                """, newHalfSize, newLevel, regionId)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> setFlags(int regionId, int flags) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, "UPDATE fx_regions SET flags = ? WHERE id = ?", flags, regionId)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteRegion(int regionId) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, "DELETE FROM fx_region_damaged_blocks WHERE region_id = ?", regionId)) {
                st.executeUpdate();
            }
            try (var st = database.prepare(connection, "DELETE FROM fx_region_member_prefs WHERE region_id = ?", regionId)) {
                st.executeUpdate();
            }
            try (var st = database.prepare(connection, "DELETE FROM fx_region_members WHERE region_id = ?", regionId)) {
                st.executeUpdate();
            }
            try (var st = database.prepare(connection, "DELETE FROM fx_regions WHERE id = ?", regionId)) {
                st.executeUpdate();
            }
        });
    }

    /** Полная очистка всех таблиц приватов (после сноса ядер в мире). */
    public CompletableFuture<Void> purgeAllRegions() {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, "DELETE FROM fx_region_damaged_blocks")) {
                st.executeUpdate();
            }
            try (var st = database.prepare(connection, "DELETE FROM fx_region_member_prefs")) {
                st.executeUpdate();
            }
            try (var st = database.prepare(connection, "DELETE FROM fx_region_members")) {
                st.executeUpdate();
            }
            try (var st = database.prepare(connection, "DELETE FROM fx_regions")) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> isDisplayNameTaken(int excludeRegionId, String name) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT 1 FROM fx_regions
                WHERE id != ? AND display_name IS NOT NULL AND TRIM(display_name) != ''
                  AND lower(display_name) = lower(?)
                LIMIT 1
                """, excludeRegionId, name.trim());
                 ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        });
    }

    public CompletableFuture<Void> updateDisplayName(int regionId, String name) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, "UPDATE fx_regions SET display_name = ? WHERE id = ?", name, regionId)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> hideBoundaryParticles(int regionId, UUID uuid) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT hide_boundary_particles FROM fx_region_member_prefs
                WHERE region_id = ? AND member_uuid = ?
                """, regionId, uuid.toString());
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) != 0;
                }
            }
            return false;
        });
    }

    public CompletableFuture<Void> setHideBoundaryParticles(int regionId, UUID uuid, boolean hide) {
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_region_member_prefs (region_id, member_uuid, hide_boundary_particles)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE hide_boundary_particles = VALUES(hide_boundary_particles)
                """
                : """
                INSERT INTO fx_region_member_prefs (region_id, member_uuid, hide_boundary_particles)
                VALUES (?, ?, ?)
                ON CONFLICT(region_id, member_uuid) DO UPDATE SET hide_boundary_particles = excluded.hide_boundary_particles
                """;
            try (var st = database.prepare(connection, sql, regionId, uuid.toString(), hide ? 1 : 0)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<DamagedBlockRow>> loadDamagedBlocks(int regionId) {
        return database.query(connection -> {
            List<DamagedBlockRow> rows = new ArrayList<>();
            try (var st = database.prepare(connection, """
                SELECT x, y, z, material, current_hp, max_hp, last_damage_ms
                FROM fx_region_damaged_blocks WHERE region_id = ?
                """, regionId);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    rows.add(new DamagedBlockRow(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("material"),
                        rs.getInt("current_hp"),
                        rs.getInt("max_hp"),
                        rs.getLong("last_damage_ms")
                    ));
                }
            }
            return rows;
        });
    }

    public CompletableFuture<Void> upsertDamagedBlock(int regionId, DamagedBlockRow row) {
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_region_damaged_blocks (region_id, x, y, z, material, current_hp, max_hp, last_damage_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  material = VALUES(material),
                  current_hp = VALUES(current_hp),
                  max_hp = VALUES(max_hp),
                  last_damage_ms = VALUES(last_damage_ms)
                """
                : """
                INSERT INTO fx_region_damaged_blocks (region_id, x, y, z, material, current_hp, max_hp, last_damage_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(region_id, x, y, z) DO UPDATE SET
                  material = excluded.material,
                  current_hp = excluded.current_hp,
                  max_hp = excluded.max_hp,
                  last_damage_ms = excluded.last_damage_ms
                """;
            try (var st = database.prepare(connection, sql, regionId, row.x(), row.y(), row.z(), row.material(), row.currentHp(), row.maxHp(), row.lastDamageMs())) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<DamagedBlockRow>> findDamaged(int regionId, int x, int y, int z) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT x, y, z, material, current_hp, max_hp, last_damage_ms
                FROM fx_region_damaged_blocks WHERE region_id = ? AND x = ? AND y = ? AND z = ?
                """, regionId, x, y, z);
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new DamagedBlockRow(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("material"),
                        rs.getInt("current_hp"),
                        rs.getInt("max_hp"),
                        rs.getLong("last_damage_ms")
                    ));
                }
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Void> deleteDamagedBlock(int regionId, int x, int y, int z) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                DELETE FROM fx_region_damaged_blocks WHERE region_id = ? AND x = ? AND y = ? AND z = ?
                """, regionId, x, y, z)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<DamagedRowWithRegion>> loadAllDamaged() {
        return database.query(connection -> {
            List<DamagedRowWithRegion> out = new ArrayList<>();
            try (var st = database.prepare(connection, """
                SELECT region_id, x, y, z, material, current_hp, max_hp, last_damage_ms
                FROM fx_region_damaged_blocks
                """);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    out.add(new DamagedRowWithRegion(
                        rs.getInt("region_id"),
                        new DamagedBlockRow(
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getString("material"),
                            rs.getInt("current_hp"),
                            rs.getInt("max_hp"),
                            rs.getLong("last_damage_ms")
                        )
                    ));
                }
            }
            return out;
        });
    }

    public record DamagedRowWithRegion(int regionId, DamagedBlockRow row) {}

    /** Любой приват, где игрок числится участником (включая владельца). */
    public CompletableFuture<Optional<Integer>> findAnyMembershipRegion(UUID uuid) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT region_id FROM fx_region_members WHERE member_uuid = ? LIMIT 1
                """, uuid.toString());
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("region_id"));
                }
            }
            return Optional.<Integer>empty();
        });
    }

    public CompletableFuture<Set<UUID>> memberUuids(int regionId) {
        return database.query(connection -> {
            Set<UUID> u = new HashSet<>();
            try (var st = database.prepare(connection, "SELECT member_uuid FROM fx_region_members WHERE region_id = ?", regionId);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    u.add(UUID.fromString(rs.getString("member_uuid")));
                }
            }
            return u;
        });
    }

    public record DamagedBlockRow(int x, int y, int z, String material, int currentHp, int maxHp, long lastDamageMs) {}
}
