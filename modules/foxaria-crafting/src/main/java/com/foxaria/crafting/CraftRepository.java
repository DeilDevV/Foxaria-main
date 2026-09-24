package com.foxaria.crafting;

import com.foxaria.api.service.DatabaseGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CraftRepository {

    private final DatabaseGateway database;

    public CraftRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<List<CustomCraftDefinition>> loadAll() {
        return database.query(connection -> {
            List<CustomCraftDefinition> list = new ArrayList<>();
            try (var statement = database.prepare(connection, """
                SELECT craft_id, recipe_json, sort_order
                FROM fx_custom_crafts ORDER BY sort_order ASC, craft_id ASC
                """);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new CustomCraftDefinition(rs.getString(1), rs.getString(2), rs.getInt(3)));
                }
            }
            return list;
        });
    }

    public CompletableFuture<Void> upsert(CustomCraftDefinition def) {
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_custom_crafts (craft_id, recipe_json, sort_order, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    recipe_json = VALUES(recipe_json),
                    sort_order = VALUES(sort_order),
                    updated_at = VALUES(updated_at)
                """
                : """
                INSERT INTO fx_custom_crafts (craft_id, recipe_json, sort_order, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(craft_id) DO UPDATE SET
                    recipe_json = excluded.recipe_json,
                    sort_order = excluded.sort_order,
                    updated_at = excluded.updated_at
                """;
            try (var statement = database.prepare(connection, sql, def.craftId(), def.recipeJson(), def.sortOrder(), System.currentTimeMillis())) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> delete(String craftId) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, "DELETE FROM fx_custom_crafts WHERE craft_id = ?", craftId)) {
                return statement.executeUpdate() > 0;
            }
        });
    }
}
