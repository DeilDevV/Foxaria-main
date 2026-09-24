package com.foxaria.kits;

import com.foxaria.api.model.KitContents;
import com.foxaria.api.model.KitDefinition;
import com.foxaria.api.service.DatabaseGateway;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KitDefinitionRepository {

    private final DatabaseGateway database;

    public KitDefinitionRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<List<KitDefinition>> listAll() {
        return database.query(connection -> {
            List<KitDefinition> list = new ArrayList<>();
            try (var statement = database.prepare(connection, """
                SELECT kit_id, display_name, cooldown_seconds, permission, required_playtime_seconds, payload
                FROM fx_kit_definitions
                ORDER BY kit_id
                """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("kit_id");
                    KitContents contents = KitPayloadCodec.decode(rs.getString("payload"));
                    list.add(new KitDefinition(
                        id,
                        rs.getString("display_name"),
                        rs.getLong("cooldown_seconds"),
                        rs.getString("permission"),
                        rs.getLong("required_playtime_seconds"),
                        contents
                    ));
                }
            }
            return list;
        });
    }

    public CompletableFuture<Void> upsert(KitDefinition definition) {
        String payload = KitPayloadCodec.encode(definition.contents());
        long now = System.currentTimeMillis();
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_kit_definitions
                (kit_id, display_name, cooldown_seconds, permission, required_playtime_seconds, payload, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    display_name = VALUES(display_name),
                    cooldown_seconds = VALUES(cooldown_seconds),
                    permission = VALUES(permission),
                    required_playtime_seconds = VALUES(required_playtime_seconds),
                    payload = VALUES(payload),
                    updated_at = VALUES(updated_at)
                """
                : """
                INSERT INTO fx_kit_definitions
                (kit_id, display_name, cooldown_seconds, permission, required_playtime_seconds, payload, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(kit_id) DO UPDATE SET
                    display_name = excluded.display_name,
                    cooldown_seconds = excluded.cooldown_seconds,
                    permission = excluded.permission,
                    required_playtime_seconds = excluded.required_playtime_seconds,
                    payload = excluded.payload,
                    updated_at = excluded.updated_at
                """;
            try (var statement = database.prepare(connection, sql,
                definition.id(),
                definition.displayName(),
                definition.cooldownSeconds(),
                definition.permission(),
                definition.requiredPlaytimeSeconds(),
                payload,
                now
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> delete(String kitId) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, "DELETE FROM fx_kit_definitions WHERE kit_id = ?", kitId)) {
                statement.executeUpdate();
            }
        });
    }
}
