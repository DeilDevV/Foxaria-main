package com.foxaria.cases;

import com.foxaria.api.service.DatabaseGateway;

import com.foxaria.cases.model.CaseLocation;
import com.foxaria.cases.model.PlacedCaseRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CaseRepository {

    private final DatabaseGateway database;

    public CaseRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Integer> keyCount(UUID playerUuid, String caseId) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT amount FROM fx_case_keys WHERE player_uuid = ? AND case_id = ?",
                playerUuid.toString(),
                caseId.toLowerCase()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Math.max(0, resultSet.getInt("amount"));
                }
            }
            return 0;
        });
    }

    public CompletableFuture<Void> addKeys(UUID playerUuid, String caseId, int amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return database.execute(connection -> {
            String id = caseId.toLowerCase();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_case_keys SET amount = amount + ? WHERE player_uuid = ? AND case_id = ?",
                amount,
                playerUuid.toString(),
                id
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_case_keys (player_uuid, case_id, amount) VALUES (?, ?, ?)",
                playerUuid.toString(),
                id,
                amount
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> consumeKey(UUID playerUuid, String caseId) {
        return database.query(connection -> {
            String id = caseId.toLowerCase();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_case_keys SET amount = amount - 1 WHERE player_uuid = ? AND case_id = ? AND amount > 0",
                playerUuid.toString(),
                id
            )) {
                return update.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Void> savePlacedCase(CaseLocation location, String caseId) {
        return database.execute(connection -> {
            try (PreparedStatement update = database.prepare(
                connection,
                """
                    UPDATE fx_placed_cases SET case_id = ?
                    WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?
                    """,
                caseId.toLowerCase(),
                location.serverId(),
                location.world(),
                location.x(),
                location.y(),
                location.z()
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                """
                    INSERT INTO fx_placed_cases (server_id, world, x, y, z, case_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                location.serverId(),
                location.world(),
                location.x(),
                location.y(),
                location.z(),
                caseId.toLowerCase()
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> removePlacedCase(CaseLocation location) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "DELETE FROM fx_placed_cases WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?",
                location.serverId(),
                location.world(),
                location.x(),
                location.y(),
                location.z()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<String>> findCaseId(CaseLocation location) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT case_id FROM fx_placed_cases WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?",
                location.serverId(),
                location.world(),
                location.x(),
                location.y(),
                location.z()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getString("case_id"));
                }
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<List<PlacedCaseRecord>> listPlacedCases(String serverId) {
        return database.query(connection -> {
            List<PlacedCaseRecord> records = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT world, x, y, z, case_id FROM fx_placed_cases WHERE server_id = ?",
                serverId
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CaseLocation location = new CaseLocation(
                        serverId,
                        resultSet.getString("world"),
                        resultSet.getInt("x"),
                        resultSet.getInt("y"),
                        resultSet.getInt("z")
                    );
                    records.add(new PlacedCaseRecord(location, resultSet.getString("case_id")));
                }
            }
            return records;
        });
    }

    public CompletableFuture<Long> addPendingReward(UUID playerUuid, String targetServer, String commandsJson) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "INSERT INTO fx_case_pending_rewards (player_uuid, target_server, commands_json, created_at) VALUES (?, ?, ?, ?)",
                playerUuid.toString(),
                targetServer.toLowerCase(),
                commandsJson,
                System.currentTimeMillis()
            )) {
                statement.executeUpdate();
            }
            try (PreparedStatement idStatement = connection.prepareStatement("SELECT last_insert_rowid() AS id");
                 ResultSet resultSet = idStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("id");
                }
            }
            return -1L;
        });
    }

    public CompletableFuture<List<PendingRewardRecord>> pendingRewards(UUID playerUuid, String targetServer) {
        return database.query(connection -> {
            List<PendingRewardRecord> records = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT id, commands_json FROM fx_case_pending_rewards WHERE player_uuid = ? AND target_server = ? ORDER BY id ASC",
                playerUuid.toString(),
                targetServer.toLowerCase()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new PendingRewardRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("commands_json")
                    ));
                }
            }
            return records;
        });
    }

    public CompletableFuture<Void> removePendingReward(long id) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "DELETE FROM fx_case_pending_rewards WHERE id = ?",
                id
            )) {
                statement.executeUpdate();
            }
        });
    }

    public record PendingRewardRecord(long id, String commandsJson) {
    }
}
