package com.foxaria.proxy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PrivilegeAuditRepository {

    record AuditEntry(long id, String actor, UUID targetUuid, String targetName, String action, String detail, long createdAt) {
    }

    private final String jdbcUrl;
    private final String username;
    private final String password;

    PrivilegeAuditRepository(String jdbcUrl, String username, String password) {
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

    private void initSchema() {
        String sql = jdbcUrl != null && jdbcUrl.toLowerCase().startsWith("jdbc:mysql:")
            ? """
            CREATE TABLE IF NOT EXISTS proxy_privilege_audit (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                actor TEXT NOT NULL,
                target_uuid TEXT NOT NULL,
                target_name TEXT NOT NULL,
                action TEXT NOT NULL,
                detail TEXT NOT NULL,
                created_at BIGINT NOT NULL
            )
            """
            : """
            CREATE TABLE IF NOT EXISTS proxy_privilege_audit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actor TEXT NOT NULL,
                target_uuid TEXT NOT NULL,
                target_name TEXT NOT NULL,
                action TEXT NOT NULL,
                detail TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """;
        try (Connection c = openConnection();
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize proxy_privilege_audit schema", e);
        }
    }

    void append(String actor, UUID targetUuid, String targetName, String action, String detail, long createdAt) {
        String sql = """
            INSERT INTO proxy_privilege_audit(actor, target_uuid, target_name, action, detail, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, actor);
            ps.setString(2, targetUuid.toString());
            ps.setString(3, targetName);
            ps.setString(4, action);
            ps.setString(5, detail);
            ps.setLong(6, createdAt);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    List<AuditEntry> recentForTarget(UUID targetUuid, int limit) {
        String sql = """
            SELECT id, actor, target_uuid, target_name, action, detail, created_at
            FROM proxy_privilege_audit
            WHERE target_uuid=?
            ORDER BY id DESC
            LIMIT ?
            """;
        List<AuditEntry> out = new ArrayList<>();
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AuditEntry(
                        rs.getLong("id"),
                        rs.getString("actor"),
                        UUID.fromString(rs.getString("target_uuid")),
                        rs.getString("target_name"),
                        rs.getString("action"),
                        rs.getString("detail"),
                        rs.getLong("created_at")
                    ));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
