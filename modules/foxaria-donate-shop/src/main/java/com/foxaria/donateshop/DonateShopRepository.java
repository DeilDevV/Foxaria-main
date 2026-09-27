package com.foxaria.donateshop;

import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class DonateShopRepository {
    private final DatabaseGateway database;

    public DonateShopRepository(DatabaseGateway database) {
        this.database = database;
    }

    public void addOffer(DonateOffer offer) {
        if (offer != null) insert(offer);
    }

    public CompletableFuture<List<DonateOffer>> listByCategory(DonateCategory category) {
        return database.query(connection -> {
            List<DonateOffer> list = new ArrayList<>();
            try (var st = database.prepare(connection, """
                SELECT id, category, price_tokens, item_blob, sort_order
                FROM fx_donate_shop_offers WHERE category = ?
                ORDER BY sort_order ASC, created_at ASC
                """, category.id()); ResultSet rs = st.executeQuery()) {
                while (rs.next()) mapRow(rs).ifPresent(list::add);
            }
            return list;
        });
    }

    public CompletableFuture<List<DonateOffer>> listAll() {
        return database.query(connection -> {
            List<DonateOffer> list = new ArrayList<>();
            try (var st = database.prepare(connection, """
                SELECT id, category, price_tokens, item_blob, sort_order
                FROM fx_donate_shop_offers ORDER BY category, sort_order ASC, created_at ASC
                """); ResultSet rs = st.executeQuery()) {
                while (rs.next()) mapRow(rs).ifPresent(list::add);
            }
            return list;
        });
    }

    public CompletableFuture<Optional<DonateOffer>> findById(String id) {
        return database.query(connection -> {
            try (var st = database.prepare(connection, """
                SELECT id, category, price_tokens, item_blob, sort_order
                FROM fx_donate_shop_offers WHERE id = ?
                """, id); ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return mapRow(rs);
            }
        });
    }

    public CompletableFuture<Void> insert(DonateOffer offer) {
        long now = System.currentTimeMillis();
        String blob = ItemStackSerializer.serialize(offer.displayItem().asOne());
        return database.execute(connection -> {
            try (var st = database.prepare(connection, """
                INSERT INTO fx_donate_shop_offers (id, category, price_tokens, item_blob, sort_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, offer.id(), offer.category().id(), offer.priceTokens(), blob, offer.sortOrder(), now)) {
                st.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> delete(String id) {
        return database.execute(connection -> {
            try (var st = database.prepare(connection, "DELETE FROM fx_donate_shop_offers WHERE id = ?", id)) {
                st.executeUpdate();
            }
        });
    }

    private static Optional<DonateOffer> mapRow(ResultSet rs) throws Exception {
        Optional<DonateCategory> category = DonateCategory.parse(rs.getString("category"));
        if (category.isEmpty()) return Optional.empty();
        ItemStack stack = ItemStackSerializer.deserialize(rs.getString("item_blob"));
        return Optional.of(new DonateOffer(rs.getString("id"), category.get(), rs.getLong("price_tokens"), stack, rs.getInt("sort_order")));
    }

    public static String newId() { return UUID.randomUUID().toString(); }
}
