package com.foxaria.proxy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

final class PrivilegeRepository {

    record PrivilegeState(String primaryGroup, String tempGroup, Long tempExpiresAt) {
    }

    private final String jdbcUrl;
    private final String username;
    private final String password;

    PrivilegeRepository(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        ensureDriverLoaded();
        initSchema();
    }

    private void ensureDriverLoaded() {
        ProxyJdbcSupport.ensureDriverLoaded(jdbcUrl);
    }

    private Connection openConnection() throws Exception {
        return username == null
            ? DriverManager.getConnection(jdbcUrl)
            : DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
    }

    private boolean isMySql() {
        return jdbcUrl != null && jdbcUrl.toLowerCase().startsWith("jdbc:mysql:");
    }

    private void initSchema() {
        String sql = isMySql() ? """
            CREATE TABLE IF NOT EXISTS proxy_privileges (
                player_uuid VARCHAR(36) PRIMARY KEY,
                primary_group VARCHAR(64) NOT NULL,
                temp_group VARCHAR(64),
                temp_expires_at BIGINT
            )
            """ : """
            CREATE TABLE IF NOT EXISTS proxy_privileges (
                player_uuid TEXT PRIMARY KEY,
                primary_group TEXT NOT NULL,
                temp_group TEXT,
                temp_expires_at INTEGER
            )
            """;
        try (Connection c = openConnection();
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize proxy_privileges schema", e);
        }
    }

    /** Look up a player's UUID by username from proxy_accounts (for offline grant/revoke). */
    java.util.Optional<UUID> findUuidByUsername(String username) {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT player_uuid FROM proxy_accounts WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return java.util.Optional.empty();
                return java.util.Optional.of(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    void setPrimary(UUID uuid, String group) {
        String upsert = isMySql() ? """
            INSERT INTO proxy_privileges(player_uuid, primary_group, temp_group, temp_expires_at)
            VALUES(?, ?, NULL, NULL)
            ON DUPLICATE KEY UPDATE primary_group=VALUES(primary_group)
            """ : """
            INSERT INTO proxy_privileges(player_uuid, primary_group, temp_group, temp_expires_at)
            VALUES(?, ?, NULL, NULL)
            ON CONFLICT(player_uuid) DO UPDATE SET primary_group=excluded.primary_group
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, group);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    void grantTemporary(UUID uuid, String group, long expiresAt) {
        String upsert = isMySql() ? """
            INSERT INTO proxy_privileges(player_uuid, primary_group, temp_group, temp_expires_at)
            VALUES(?, 'default', ?, ?)
            ON DUPLICATE KEY UPDATE temp_group=VALUES(temp_group), temp_expires_at=VALUES(temp_expires_at)
            """ : """
            INSERT INTO proxy_privileges(player_uuid, primary_group, temp_group, temp_expires_at)
            VALUES(?, 'default', ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET temp_group=excluded.temp_group, temp_expires_at=excluded.temp_expires_at
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, group);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    void revokeTemporary(UUID uuid) {
        String sql = "UPDATE proxy_privileges SET temp_group=NULL, temp_expires_at=NULL WHERE player_uuid=?";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    void resetToDefault(UUID uuid, String defaultGroup) {
        String upsert = isMySql() ? """
            INSERT INTO proxy_privileges(player_uuid, primary_group, temp_group, temp_expires_at)
            VALUES(?, ?, NULL, NULL)
            ON DUPLICATE KEY UPDATE primary_group=VALUES(primary_group), temp_group=NULL, temp_expires_at=NULL
            """ : """
            INSERT INTO proxy_privileges(player_uuid, primary_group, temp_group, temp_expires_at)
            VALUES(?, ?, NULL, NULL)
            ON CONFLICT(player_uuid) DO UPDATE SET primary_group=excluded.primary_group, temp_group=NULL, temp_expires_at=NULL
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, defaultGroup);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    PrivilegeState state(UUID uuid) {
        String sql = "SELECT primary_group, temp_group, temp_expires_at FROM proxy_privileges WHERE player_uuid=?";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long raw = rs.getLong("temp_expires_at");
                Long until = rs.wasNull() ? null : raw;
                return new PrivilegeState(
                    rs.getString("primary_group"),
                    rs.getString("temp_group"),
                    until
                );
            }
        } catch (Exception e) {
            return null;
        }
    }

    String resolveActiveGroup(UUID uuid, String fallback, long now) {
        String sql = "SELECT primary_group, temp_group, temp_expires_at FROM proxy_privileges WHERE player_uuid=?";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return fallback;
                }
                String primary = rs.getString("primary_group");
                String temp = rs.getString("temp_group");
                long until = rs.getLong("temp_expires_at");
                if (temp != null && !temp.isBlank() && until > now) {
                    return temp;
                }
                if (temp != null && !temp.isBlank() && until <= now) {
                    clearExpiredTemp(uuid);
                }
                return primary == null || primary.isBlank() ? fallback : primary;
            }
        } catch (Exception e) {
            return fallback;
        }
    }

    private void clearExpiredTemp(UUID uuid) {
        String sql = "UPDATE proxy_privileges SET temp_group=NULL, temp_expires_at=NULL WHERE player_uuid=?";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
