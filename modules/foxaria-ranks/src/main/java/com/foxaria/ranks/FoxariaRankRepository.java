package com.foxaria.ranks;

import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FoxariaRankRepository {

    private final DatabaseGateway database;

    public FoxariaRankRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<String> primaryGroup(UUID playerUuid, String defaultGroup) {
        return database.query(connection -> {
            String resolvedGroup = selectPrimaryGroup(connection, playerUuid);
            if (resolvedGroup != null) {
                return resolvedGroup;
            }
            upsertPrimaryGroup(connection, playerUuid, normalize(defaultGroup));
            return normalize(defaultGroup);
        });
    }

    public CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String group) {
        return database.execute(connection -> upsertPrimaryGroup(connection, playerUuid, normalize(group)));
    }

    public CompletableFuture<Void> grantTemporaryGroup(UUID playerUuid, String group, long expiresAt) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    INSERT INTO fx_rank_grants (id, player_uuid, group_name, granted_at, expires_at, active)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                UUID.randomUUID().toString(),
                playerUuid.toString(),
                normalize(group),
                System.currentTimeMillis(),
                expiresAt,
                true
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> removeGroup(UUID playerUuid, String group, String defaultGroup) {
        return database.execute(connection -> {
            String normalizedGroup = normalize(group);
            String normalizedDefault = normalize(defaultGroup);
            try (PreparedStatement primaryUpdate = database.prepare(
                connection,
                """
                    UPDATE fx_player_ranks
                    SET primary_group = ?, updated_at = ?
                    WHERE player_uuid = ? AND LOWER(primary_group) = LOWER(?)
                    """,
                normalizedDefault,
                System.currentTimeMillis(),
                playerUuid.toString(),
                normalizedGroup
            )) {
                primaryUpdate.executeUpdate();
            }
            try (PreparedStatement grantUpdate = database.prepare(
                connection,
                """
                    UPDATE fx_rank_grants
                    SET active = FALSE
                    WHERE player_uuid = ? AND LOWER(group_name) = LOWER(?) AND active = TRUE
                    """,
                playerUuid.toString(),
                normalizedGroup
            )) {
                grantUpdate.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> grantTemporaryPermission(UUID playerUuid, String permission, long expiresAt, String reason) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    INSERT INTO fx_permission_grants (id, player_uuid, permission, reason, granted_at, expires_at, active)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                UUID.randomUUID().toString(),
                playerUuid.toString(),
                normalize(permission),
                reason,
                System.currentTimeMillis(),
                expiresAt,
                true
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<RankSnapshot> snapshot(UUID playerUuid, String defaultGroup) {
        return database.query(connection -> {
            String primaryGroup = selectPrimaryGroup(connection, playerUuid);
            if (primaryGroup == null) {
                primaryGroup = normalize(defaultGroup);
                upsertPrimaryGroup(connection, playerUuid, primaryGroup);
            }

            long now = System.currentTimeMillis();
            List<String> temporaryGroups = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    SELECT group_name
                    FROM fx_rank_grants
                    WHERE player_uuid = ?
                      AND active = TRUE
                      AND (expires_at = 0 OR expires_at > ?)
                    """,
                playerUuid.toString(),
                now
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    temporaryGroups.add(normalize(resultSet.getString("group_name")));
                }
            }

            List<String> temporaryPermissions = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    SELECT permission
                    FROM fx_permission_grants
                    WHERE player_uuid = ?
                      AND active = TRUE
                      AND (expires_at = 0 OR expires_at > ?)
                    """,
                playerUuid.toString(),
                now
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    temporaryPermissions.add(normalize(resultSet.getString("permission")));
                }
            }

            return new RankSnapshot(primaryGroup, temporaryGroups, temporaryPermissions);
        });
    }

    public CompletableFuture<Void> cleanupExpired(long now) {
        return database.execute(connection -> {
            try (PreparedStatement groups = database.prepare(
                connection,
                """
                    UPDATE fx_rank_grants
                    SET active = FALSE
                    WHERE active = TRUE AND expires_at > 0 AND expires_at <= ?
                    """,
                now
            )) {
                groups.executeUpdate();
            }
            try (PreparedStatement permissions = database.prepare(
                connection,
                """
                    UPDATE fx_permission_grants
                    SET active = FALSE
                    WHERE active = TRUE AND expires_at > 0 AND expires_at <= ?
                    """,
                now
            )) {
                permissions.executeUpdate();
            }
        });
    }

    private String selectPrimaryGroup(java.sql.Connection connection, UUID playerUuid) throws Exception {
        try (PreparedStatement statement = database.prepare(
            connection,
            "SELECT primary_group FROM fx_player_ranks WHERE player_uuid = ?",
            playerUuid.toString()
        );
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return normalize(resultSet.getString("primary_group"));
            }
        }
        return null;
    }

    private void upsertPrimaryGroup(java.sql.Connection connection, UUID playerUuid, String group) throws Exception {
        if (selectPrimaryGroup(connection, playerUuid) == null) {
            try (PreparedStatement insert = database.prepare(
                connection,
                """
                    INSERT INTO fx_player_ranks (player_uuid, primary_group, updated_at)
                    VALUES (?, ?, ?)
                    """,
                playerUuid.toString(),
                group,
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
            }
            return;
        }
        try (PreparedStatement update = database.prepare(
            connection,
            """
                UPDATE fx_player_ranks
                SET primary_group = ?, updated_at = ?
                WHERE player_uuid = ?
                """,
            group,
            System.currentTimeMillis(),
            playerUuid.toString()
        )) {
            update.executeUpdate();
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "default" : value.toLowerCase(Locale.ROOT);
    }

    public record RankSnapshot(String primaryGroup, List<String> temporaryGroups, List<String> temporaryPermissions) {
    }
}
