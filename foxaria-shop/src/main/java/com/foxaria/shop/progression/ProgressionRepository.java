package com.foxaria.shop.progression;

import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProgressionRepository {

    private final DatabaseGateway database;

    public ProgressionRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<PlayerProgressionState> load(UUID uuid) {
        return database.query(connection -> {
            try (var st = database.prepare(connection,
                "SELECT knowledge_level, current_quest_id, quest_progress, quest_objective_csv, completed_quests FROM fx_player_progression WHERE player_uuid = ?",
                uuid.toString());
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return new PlayerProgressionState(
                        uuid,
                        rs.getInt("knowledge_level"),
                        rs.getString("current_quest_id"),
                        rs.getLong("quest_progress"),
                        rs.getString("quest_objective_csv"),
                        PlayerProgressionState.deserializeCompleted(rs.getString("completed_quests"))
                    );
                }
            }
            return new PlayerProgressionState(uuid, 1, null, 0L, null, Set.of());
        });
    }

    public CompletableFuture<Void> save(PlayerProgressionState state) {
        return database.execute(connection -> {
            String completed = PlayerProgressionState.serializeCompleted(state.completedQuests());
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_player_progression (player_uuid, knowledge_level, current_quest_id, quest_progress, quest_objective_csv, completed_quests)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    knowledge_level = VALUES(knowledge_level),
                    current_quest_id = VALUES(current_quest_id),
                    quest_progress = VALUES(quest_progress),
                    quest_objective_csv = VALUES(quest_objective_csv),
                    completed_quests = VALUES(completed_quests)
                """
                : """
                INSERT INTO fx_player_progression (player_uuid, knowledge_level, current_quest_id, quest_progress, quest_objective_csv, completed_quests)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    knowledge_level = excluded.knowledge_level,
                    current_quest_id = excluded.current_quest_id,
                    quest_progress = excluded.quest_progress,
                    quest_objective_csv = excluded.quest_objective_csv,
                    completed_quests = excluded.completed_quests
                """;
            try (var st = database.prepare(connection, sql,
                state.playerUuid().toString(),
                state.knowledgeLevel(),
                state.currentQuestId(),
                state.questProgress(),
                state.questObjectiveCsv(),
                completed
            )) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> ensureRow(UUID uuid, String initialQuestId) {
        return database.execute(connection -> {
            try (var check = database.prepare(connection,
                "SELECT 1 FROM fx_player_progression WHERE player_uuid = ?", uuid.toString());
                 ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
            try (var ins = database.prepare(connection, """
                INSERT INTO fx_player_progression (player_uuid, knowledge_level, current_quest_id, quest_progress, quest_objective_csv, completed_quests)
                VALUES (?, 1, ?, 0, NULL, '')
                """, uuid.toString(), initialQuestId)) {
                ins.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<ProgressionShopOffer>> listShopOffers(String category) {
        return database.query(connection -> {
            List<ProgressionShopOffer> list = new ArrayList<>();
            try (var st = database.prepare(connection, """
                SELECT id, category, required_knowledge, price, item_blob, item_template, sort_order
                FROM fx_progression_shop_offers WHERE category = ? ORDER BY sort_order ASC, created_at ASC
                """, category);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    list.add(readOffer(rs));
                }
            }
            return list;
        });
    }

    public CompletableFuture<List<ProgressionShopOffer>> listAllShopOffers() {
        return database.query(connection -> {
            List<ProgressionShopOffer> list = new ArrayList<>();
            try (var st = database.prepare(connection, """
                SELECT id, category, required_knowledge, price, item_blob, item_template, sort_order
                FROM fx_progression_shop_offers ORDER BY category, sort_order ASC
                """);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    list.add(readOffer(rs));
                }
            }
            return list;
        });
    }

    public CompletableFuture<Optional<ProgressionShopOffer>> findShopOffer(String id) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT id, category, required_knowledge, price, item_blob, item_template, sort_order
                FROM fx_progression_shop_offers WHERE id = ?
                """, id);
                 ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readOffer(rs));
            }
        });
    }

    public CompletableFuture<Void> insertShopOffer(ProgressionShopOffer offer) {
        long now = System.currentTimeMillis();
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                INSERT INTO fx_progression_shop_offers
                (id, category, required_knowledge, price, item_blob, item_template, sort_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                offer.id(),
                offer.category(),
                offer.requiredKnowledge(),
                offer.price().setScale(2, RoundingMode.HALF_UP),
                offer.itemBlob() == null ? null : offer.itemBlob(),
                offer.itemTemplate() == null ? "" : offer.itemTemplate(),
                offer.sortOrder(),
                now
            )) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteShopOffer(String id) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, "DELETE FROM fx_progression_shop_offers WHERE id = ?", id)) {
                st.executeUpdate();
            }
        });
    }

    private static ProgressionShopOffer readOffer(ResultSet rs) throws Exception {
        return new ProgressionShopOffer(
            rs.getString("id"),
            rs.getString("category"),
            rs.getInt("required_knowledge"),
            rs.getBigDecimal("price").setScale(2, RoundingMode.HALF_UP),
            rs.getString("item_blob"),
            rs.getString("item_template"),
            rs.getInt("sort_order")
        );
    }

    public static ItemStack deserializeItem(ProgressionShopOffer offer) {
        if (offer.itemBlob() != null && !offer.itemBlob().isBlank()) {
            return ItemStackSerializer.deserialize(offer.itemBlob());
        }
        return null;
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
