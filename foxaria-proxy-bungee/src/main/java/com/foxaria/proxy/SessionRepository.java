package com.foxaria.proxy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Persists proxy authentication sessions to the database.
 * Survives proxy restarts: valid sessions are restored on startup.
 */
final class SessionRepository {

    record SessionRecord(UUID playerUuid, String ipAddress, long expiresAt) {}

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Logger logger;

    SessionRepository(String jdbcUrl, String username, String password, Logger logger) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.logger = logger;
        ProxyJdbcSupport.ensureDriverLoaded(jdbcUrl);
        initSchema();
        cleanExpired();
    }

    private boolean isMySql() {
        return jdbcUrl != null && jdbcUrl.toLowerCase().startsWith("jdbc:mysql:");
    }

    private Connection openConnection() throws Exception {
        return username == null
            ? DriverManager.getConnection(jdbcUrl)
            : DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
    }

    private void initSchema() {
        String sql = isMySql() ? """
            CREATE TABLE IF NOT EXISTS proxy_sessions (
                player_uuid VARCHAR(36) PRIMARY KEY,
                ip_address VARCHAR(64),
                expires_at BIGINT NOT NULL
            )
            """ : """
            CREATE TABLE IF NOT EXISTS proxy_sessions (
                player_uuid TEXT PRIMARY KEY,
                ip_address TEXT,
                expires_at INTEGER NOT NULL
            )
            """;
        try (Connection c = openConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (Exception e) {
            logger.warning("[SessionRepository] Schema init failed: " + e.getMessage());
        }
    }

    void save(UUID playerUuid, String ip, long expiresAt) {
        String sql = isMySql()
            ? "INSERT INTO proxy_sessions (player_uuid, ip_address, expires_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE ip_address=VALUES(ip_address), expires_at=VALUES(expires_at)"
            : "INSERT OR REPLACE INTO proxy_sessions (player_uuid, ip_address, expires_at) VALUES (?, ?, ?)";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("[SessionRepository] save failed: " + e.getMessage());
        }
    }

    void delete(UUID playerUuid) {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM proxy_sessions WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("[SessionRepository] delete failed: " + e.getMessage());
        }
    }

    /** Load all non-expired sessions (called on proxy startup to restore in-memory state). */
    Map<UUID, SessionRecord> loadActive(long nowMs) {
        Map<UUID, SessionRecord> result = new HashMap<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("SELECT player_uuid, ip_address, expires_at FROM proxy_sessions WHERE expires_at > ?")) {
            ps.setLong(1, nowMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String ip = rs.getString("ip_address");
                    long exp = rs.getLong("expires_at");
                    result.put(uuid, new SessionRecord(uuid, ip, exp));
                }
            }
        } catch (Exception e) {
            logger.warning("[SessionRepository] loadActive failed: " + e.getMessage());
        }
        return result;
    }

    /** Find a single active session for a player — used as DB fallback in isSessionValid. */
    SessionRecord findActive(UUID playerUuid, long nowMs) {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT player_uuid, ip_address, expires_at FROM proxy_sessions WHERE player_uuid = ? AND expires_at > ? LIMIT 1"
             )) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, nowMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new SessionRecord(playerUuid, rs.getString("ip_address"), rs.getLong("expires_at"));
            }
        } catch (Exception e) {
            logger.warning("[SessionRepository] findActive failed: " + e.getMessage());
            return null;
        }
    }

    void cleanExpired() {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM proxy_sessions WHERE expires_at <= ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("[SessionRepository] cleanExpired failed: " + e.getMessage());
        }
    }
}
