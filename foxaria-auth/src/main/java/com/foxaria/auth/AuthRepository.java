package com.foxaria.auth;

import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AuthRepository {

    private final DatabaseGateway database;

    public AuthRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<AuthAccount> findByUsername(String username) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_auth_accounts WHERE username = ?",
                username
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new AuthAccount(
                    resultSet.getString("username"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("salt"),
                    resultSet.getInt("iterations"),
                    resultSet.getString("last_uuid"),
                    resultSet.getLong("registered_at"),
                    resultSet.getLong("last_login_at")
                );
            }
        });
    }

    public CompletableFuture<AuthAccount> findByUuid(UUID playerUuid) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_auth_accounts WHERE last_uuid = ?",
                playerUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new AuthAccount(
                    resultSet.getString("username"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("salt"),
                    resultSet.getInt("iterations"),
                    resultSet.getString("last_uuid"),
                    resultSet.getLong("registered_at"),
                    resultSet.getLong("last_login_at")
                );
            }
        });
    }

    public CompletableFuture<Void> createAccount(String username, UUID playerUuid, PasswordHasher.HashPayload payload) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    INSERT INTO fx_auth_accounts (username, password_hash, salt, iterations, last_uuid, registered_at, updated_at, last_login_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                username,
                payload.hash(),
                payload.salt(),
                payload.iterations(),
                playerUuid.toString(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                0L
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> recordLogin(String username, UUID playerUuid) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_auth_accounts SET username = ?, last_uuid = ?, updated_at = ?, last_login_at = ? WHERE username = ? OR last_uuid = ?",
                username,
                playerUuid.toString(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                username,
                playerUuid.toString()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public record AuthAccount(
        String username,
        String passwordHash,
        String salt,
        int iterations,
        String lastUuid,
        long registeredAt,
        long lastLoginAt
    ) {
    }
}
