package com.foxaria.playershops;

import com.foxaria.api.model.PlayerShop;
import com.foxaria.api.model.PlayerShopOffer;
import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerShopRepository {

    private final DatabaseGateway database;

    public PlayerShopRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Optional<PlayerShop>> findByOwner(UUID ownerUuid) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(connection,
                "SELECT * FROM fx_player_shops WHERE owner_uuid = ?",
                ownerUuid.toString());
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readShop(resultSet));
            }
        });
    }

    public CompletableFuture<Void> createShop(PlayerShop shop) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(connection,
                "INSERT INTO fx_player_shops (id, owner_uuid, name, description, open, tax_percent, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                shop.id(),
                shop.ownerUuid().toString(),
                shop.name(),
                shop.description(),
                shop.open(),
                shop.taxPercent(),
                shop.createdAt())) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<PlayerShop>> listOpenShops() {
        return database.query(connection -> {
            List<PlayerShop> shops = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(connection,
                "SELECT * FROM fx_player_shops WHERE open = TRUE ORDER BY created_at DESC");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    shops.add(readShop(resultSet));
                }
            }
            return shops;
        });
    }

    public CompletableFuture<Optional<PlayerShop>> findById(String shopId) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(connection,
                "SELECT * FROM fx_player_shops WHERE id = ?",
                shopId);
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readShop(resultSet));
            }
        });
    }

    public CompletableFuture<Void> setOpen(String shopId, boolean open) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(connection,
                "UPDATE fx_player_shops SET open = ? WHERE id = ?",
                open,
                shopId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Integer> countOffers(String shopId) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(connection,
                "SELECT COUNT(*) AS c FROM fx_player_shop_offers WHERE shop_id = ? AND active = TRUE",
                shopId);
                 ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("c") : 0;
            }
        });
    }

    public CompletableFuture<Void> createOffer(PlayerShopOffer offer) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(connection,
                """
                    INSERT INTO fx_player_shop_offers
                    (id, shop_id, item_base64, fingerprint, price, stock, active, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                offer.id(),
                offer.shopId(),
                ItemStackSerializer.serialize(offer.item()),
                offer.fingerprint(),
                offer.price(),
                offer.stock(),
                offer.active(),
                offer.createdAt())) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<PlayerShopOffer>> offers(String shopId, boolean includeInactive) {
        return database.query(connection -> {
            List<PlayerShopOffer> offers = new ArrayList<>();
            String sql = includeInactive
                ? "SELECT * FROM fx_player_shop_offers WHERE shop_id = ? ORDER BY created_at DESC"
                : "SELECT * FROM fx_player_shop_offers WHERE shop_id = ? AND active = TRUE AND stock > 0 ORDER BY created_at DESC";
            try (PreparedStatement statement = database.prepare(connection, sql, shopId);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    offers.add(readOffer(resultSet));
                }
            }
            return offers;
        });
    }

    public CompletableFuture<Optional<ReservedPurchase>> reserveOffer(String offerId) {
        return database.query(connection -> {
            connection.setAutoCommit(false);
            try {
                PlayerShopOffer offer;
                PlayerShop shop;
                try (PreparedStatement select = database.prepare(connection,
                    """
                        SELECT o.*, s.owner_uuid, s.tax_percent, s.open, s.name, s.description, s.created_at AS shop_created_at
                        FROM fx_player_shop_offers o
                        JOIN fx_player_shops s ON s.id = o.shop_id
                        WHERE o.id = ? AND o.active = TRUE AND o.stock > 0
                        """,
                    offerId);
                     ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        return Optional.empty();
                    }
                    offer = readOffer(resultSet);
                    shop = new PlayerShop(
                        resultSet.getString("shop_id"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getBoolean("open"),
                        resultSet.getDouble("tax_percent"),
                        resultSet.getLong("shop_created_at")
                    );
                }
                try (PreparedStatement update = database.prepare(connection,
                    "UPDATE fx_player_shop_offers SET stock = stock - 1, active = CASE WHEN stock - 1 <= 0 THEN FALSE ELSE TRUE END WHERE id = ? AND stock > 0 AND active = TRUE",
                    offerId)) {
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                connection.commit();
                return Optional.of(new ReservedPurchase(shop, offer));
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    private PlayerShop readShop(ResultSet resultSet) throws Exception {
        return new PlayerShop(
            resultSet.getString("id"),
            UUID.fromString(resultSet.getString("owner_uuid")),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getBoolean("open"),
            resultSet.getDouble("tax_percent"),
            resultSet.getLong("created_at")
        );
    }

    private PlayerShopOffer readOffer(ResultSet resultSet) throws Exception {
        return new PlayerShopOffer(
            resultSet.getString("id"),
            resultSet.getString("shop_id"),
            ItemStackSerializer.deserialize(resultSet.getString("item_base64")),
            resultSet.getString("fingerprint"),
            resultSet.getBigDecimal("price"),
            resultSet.getInt("stock"),
            resultSet.getBoolean("active"),
            resultSet.getLong("created_at")
        );
    }

    public record ReservedPurchase(PlayerShop shop, PlayerShopOffer offer) {
    }
}
