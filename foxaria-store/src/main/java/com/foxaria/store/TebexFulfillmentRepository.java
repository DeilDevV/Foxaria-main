package com.foxaria.store;

import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TebexFulfillmentRepository {

    private final DatabaseGateway database;

    public TebexFulfillmentRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Void> enqueue(String externalTxnId, UUID playerUuid, String packageId, String action, String payloadJson) {
        return database.execute(connection -> {
            try (PreparedStatement existing = database.prepare(
                connection,
                "SELECT id FROM fx_tebex_fulfillments WHERE external_txn_id = ?",
                externalTxnId
            );
                 ResultSet ignored = existing.executeQuery()) {
                if (ignored.next()) {
                    return;
                }
            }
            try (PreparedStatement statement = database.prepare(
                connection,
                "INSERT INTO fx_tebex_fulfillments (id, external_txn_id, player_uuid, package_id, action, status, payload_json, attempts, last_error, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, 0, '', ?, ?)",
                UUID.randomUUID().toString(),
                externalTxnId,
                playerUuid.toString(),
                packageId,
                action,
                payloadJson,
                System.currentTimeMillis(),
                System.currentTimeMillis()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<TebexRequest>> pending(int limit) {
        return database.query(connection -> {
            List<TebexRequest> requests = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_tebex_fulfillments WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC LIMIT ?",
                limit
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(new TebexRequest(
                        resultSet.getString("id"),
                        resultSet.getString("external_txn_id"),
                        UUID.fromString(resultSet.getString("player_uuid")),
                        resultSet.getString("package_id"),
                        resultSet.getString("action"),
                        resultSet.getString("payload_json"),
                        resultSet.getInt("attempts")
                    ));
                }
            }
            return requests;
        });
    }

    public CompletableFuture<Void> markFulfilled(String id) {
        return updateStatus(id, "FULFILLED", "");
    }

    public CompletableFuture<Boolean> claimForProcessing(String id) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_tebex_fulfillments SET status = 'PROCESSING', updated_at = ? WHERE id = ? AND status IN ('PENDING', 'FAILED')",
                System.currentTimeMillis(),
                id
            )) {
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Void> markFailed(String id, String error) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_tebex_fulfillments SET status = 'FAILED', attempts = attempts + 1, last_error = ?, updated_at = ? WHERE id = ?",
                error,
                System.currentTimeMillis(),
                id
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> upsertSubscription(String externalTxnId, UUID playerUuid, String packageId, String groupName, long expiresAt, String status) {
        return database.execute(connection -> {
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_store_subscriptions SET player_uuid = ?, package_id = ?, group_name = ?, expires_at = ?, status = ?, updated_at = ? WHERE external_txn_id = ?",
                playerUuid.toString(),
                packageId,
                groupName,
                expiresAt,
                status,
                System.currentTimeMillis(),
                externalTxnId
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_store_subscriptions (external_txn_id, player_uuid, package_id, group_name, started_at, expires_at, status, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                externalTxnId,
                playerUuid.toString(),
                packageId,
                groupName,
                System.currentTimeMillis(),
                expiresAt,
                status,
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateSubscriptionStatus(String externalTxnId, String status) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_store_subscriptions SET status = ?, updated_at = ? WHERE external_txn_id = ?",
                status,
                System.currentTimeMillis(),
                externalTxnId
            )) {
                statement.executeUpdate();
            }
        });
    }

    private CompletableFuture<Void> updateStatus(String id, String status, String error) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_tebex_fulfillments SET status = ?, last_error = ?, updated_at = ? WHERE id = ?",
                status,
                error,
                System.currentTimeMillis(),
                id
            )) {
                statement.executeUpdate();
            }
        });
    }

    public record TebexRequest(String id, String externalTxnId, UUID playerUuid, String packageId, String action, String payloadJson, int attempts) {
    }
}
