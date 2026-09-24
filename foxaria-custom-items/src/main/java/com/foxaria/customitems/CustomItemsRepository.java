package com.foxaria.customitems;

import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CustomItemsRepository {

    private final DatabaseGateway database;

    public CustomItemsRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Void> registerIssued(UUID itemId, String itemType) {
        return database.execute(connection -> {
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_custom_item_instances SET item_type = ?, created_at = ? WHERE id = ?",
                itemType,
                System.currentTimeMillis(),
                itemId.toString()
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_custom_item_instances (id, item_type, created_at, consumed_at) VALUES (?, ?, ?, 0)",
                itemId.toString(),
                itemType,
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
            } catch (Exception ignored) {
            }
        });
    }

    public CompletableFuture<Boolean> consumeItem(UUID itemId, String itemType) {
        return database.query(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_custom_item_instances SET consumed_at = ? WHERE id = ? AND consumed_at = 0",
                now,
                itemId.toString()
            )) {
                if (update.executeUpdate() == 1) {
                    return true;
                }
            }
            try (PreparedStatement read = database.prepare(connection, "SELECT consumed_at FROM fx_custom_item_instances WHERE id = ?", itemId.toString());
                 ResultSet resultSet = read.executeQuery()) {
                if (resultSet.next()) {
                    return false;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_custom_item_instances (id, item_type, created_at, consumed_at) VALUES (?, ?, ?, ?)",
                itemId.toString(),
                itemType,
                now,
                now
            )) {
                insert.executeUpdate();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> claimReward(UUID playerUuid, String rewardKey) {
        return database.query(connection -> {
            try (PreparedStatement read = database.prepare(
                connection,
                "SELECT reward_key FROM fx_reward_claims WHERE player_uuid = ? AND reward_key = ?",
                playerUuid.toString(),
                rewardKey
            );
                 ResultSet resultSet = read.executeQuery()) {
                if (resultSet.next()) {
                    return false;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_reward_claims (player_uuid, reward_key, claimed_at) VALUES (?, ?, ?)",
                playerUuid.toString(),
                rewardKey,
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
                return true;
            }
        });
    }

    public CompletableFuture<Void> logCrateOpen(UUID playerUuid, String crateId, String rewardId, String action) {
        return database.execute(connection -> {
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_crate_open_logs (id, player_uuid, crate_id, reward_id, action, opened_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                playerUuid.toString(),
                crateId,
                rewardId,
                action,
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
            }
        });
    }
}
