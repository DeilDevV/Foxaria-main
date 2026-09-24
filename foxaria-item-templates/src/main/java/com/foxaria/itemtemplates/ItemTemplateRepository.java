package com.foxaria.itemtemplates;

import com.foxaria.api.service.DatabaseGateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ItemTemplateRepository {

    private final DatabaseGateway database;

    public ItemTemplateRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Map<String, TemplateRow>> loadAllRows() {
        return database.query(connection -> {
            Map<String, TemplateRow> map = new LinkedHashMap<>();
            try (var statement = database.prepare(connection, """
                SELECT id, stack_data, notes, COALESCE(enchant_glint, 0), COALESCE(crafting_core, 0)
                FROM fx_item_templates ORDER BY id ASC
                """);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    map.put(id, new TemplateRow(id, rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5)));
                }
            }
            return map;
        });
    }

    public CompletableFuture<Optional<TemplateRow>> findRow(String id) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                SELECT id, stack_data, notes, COALESCE(enchant_glint, 0), COALESCE(crafting_core, 0)
                FROM fx_item_templates WHERE id = ?
                """, id);
                 var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new TemplateRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5)));
            }
        });
    }

    public CompletableFuture<Void> upsert(String id, String stackData, String notes, int enchantGlint, int craftingCore) {
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_item_templates (id, stack_data, notes, enchant_glint, crafting_core, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    stack_data = VALUES(stack_data),
                    notes = VALUES(notes),
                    enchant_glint = VALUES(enchant_glint),
                    crafting_core = VALUES(crafting_core),
                    updated_at = VALUES(updated_at)
                """
                : """
                INSERT INTO fx_item_templates (id, stack_data, notes, enchant_glint, crafting_core, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    stack_data = excluded.stack_data,
                    notes = excluded.notes,
                    enchant_glint = excluded.enchant_glint,
                    crafting_core = excluded.crafting_core,
                    updated_at = excluded.updated_at
                """;
            try (var statement = database.prepare(connection, sql, id, stackData, notes == null ? "" : notes, enchantGlint, craftingCore, System.currentTimeMillis())) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> delete(String id) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, "DELETE FROM fx_item_templates WHERE id = ?", id)) {
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<List<String>> listIds() {
        return database.query(connection -> {
            List<String> ids = new ArrayList<>();
            try (var statement = database.prepare(connection, "SELECT id FROM fx_item_templates ORDER BY id ASC");
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
            return ids;
        });
    }
}
