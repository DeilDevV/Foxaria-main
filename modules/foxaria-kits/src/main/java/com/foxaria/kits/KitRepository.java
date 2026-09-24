package com.foxaria.kits;

import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class KitRepository {

    private final DatabaseGateway database;

    public KitRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Map<String, Long>> claims(UUID playerUuid) {
        return database.query(connection -> {
            Map<String, Long> claims = new HashMap<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT kit_id, claimed_at FROM fx_kit_claims WHERE player_uuid = ?",
                playerUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    claims.put(resultSet.getString("kit_id"), resultSet.getLong("claimed_at"));
                }
            }
            return claims;
        });
    }

    public CompletableFuture<Void> markClaimed(UUID playerUuid, String kitId) {
        return database.execute(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_kit_claims SET claimed_at = ? WHERE player_uuid = ? AND kit_id = ?",
                now,
                playerUuid.toString(),
                kitId
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_kit_claims (player_uuid, kit_id, claimed_at) VALUES (?, ?, ?)",
                playerUuid.toString(),
                kitId,
                now
            )) {
                insert.executeUpdate();
            }
        });
    }
}
