package com.foxaria.proxy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

final class AuthRepository {

    record Account(UUID uuid, String username, String hash, String salt) {
    }

    private final String jdbcUrl;
    private final String username;
    private final String password;

    AuthRepository(String jdbcUrl, String username, String password) {
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
        String sql = jdbcUrl != null && jdbcUrl.toLowerCase().startsWith("jdbc:mysql:") ? """
            CREATE TABLE IF NOT EXISTS proxy_accounts (
                player_uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(64) NOT NULL,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                last_login_at BIGINT
            )
            """ : """
            CREATE TABLE IF NOT EXISTS proxy_accounts (
                player_uuid TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_login_at INTEGER
            )
            """;
        try (Connection c = openConnection();
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize proxy_accounts schema", e);
        }
    }

    /**
     * UUID аккаунта на прокси (тот же, что у игрока в сети после входа) — нужен для /unpunish,
     * если Mojang/offline UUID не совпадают со строкей в proxy_punishments (старые записи).
     */
    Optional<UUID> findUuidByUsernameIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT player_uuid FROM proxy_accounts WHERE lower(trim(username)) = lower(trim(?)) LIMIT 1";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    Optional<Account> find(UUID uuid) {
        String sql = "SELECT player_uuid, username, password_hash, password_salt FROM proxy_accounts WHERE player_uuid=?";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Account(
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("password_salt")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load account for " + uuid, e);
        }
    }

    boolean register(UUID uuid, String username, String hash, String salt, long now) {
        String sql = """
            INSERT INTO proxy_accounts(player_uuid, username, password_hash, password_salt, created_at, last_login_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setString(3, hash);
            ps.setString(4, salt);
            ps.setLong(5, now);
            ps.setLong(6, now);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    void touchLogin(UUID uuid, String username, long now) {
        String sql = "UPDATE proxy_accounts SET username=?, last_login_at=? WHERE player_uuid=?";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setLong(2, now);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
