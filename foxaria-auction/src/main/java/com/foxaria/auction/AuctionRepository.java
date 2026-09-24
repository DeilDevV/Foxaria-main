package com.foxaria.auction;

import com.foxaria.api.model.AuctionListing;
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

public final class AuctionRepository {

    private final DatabaseGateway database;

    public AuctionRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Void> create(AuctionListing listing) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    INSERT INTO fx_auction_listings
                    (id, seller_uuid, buyer_uuid, item_base64, fingerprint, price, fee, status, expires_at, claimed_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                listing.id(),
                listing.sellerUuid().toString(),
                listing.buyerUuid() == null ? null : listing.buyerUuid().toString(),
                ItemStackSerializer.serialize(listing.item()),
                listing.fingerprint(),
                listing.price(),
                listing.fee(),
                listing.status(),
                listing.expiresAt(),
                0L,
                listing.createdAt()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<AuctionListing>> active() {
        return database.query(connection -> {
            List<AuctionListing> listings = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_auction_listings WHERE status = 'ACTIVE' AND expires_at > ? ORDER BY created_at DESC",
                System.currentTimeMillis()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    listings.add(read(resultSet));
                }
            }
            return listings;
        });
    }

    public CompletableFuture<Optional<AuctionListing>> reserve(String listingId, UUID buyerUuid) {
        return database.query(connection -> {
            connection.setAutoCommit(false);
            try {
                AuctionListing listing;
                try (PreparedStatement select = database.prepare(
                    connection,
                    "SELECT * FROM fx_auction_listings WHERE id = ? AND status = 'ACTIVE' AND expires_at > ?",
                    listingId,
                    System.currentTimeMillis()
                );
                     ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        return Optional.empty();
                    }
                    listing = read(resultSet);
                }
                try (PreparedStatement update = database.prepare(
                    connection,
                    "UPDATE fx_auction_listings SET status = 'RESERVED', buyer_uuid = ? WHERE id = ? AND status = 'ACTIVE'",
                    buyerUuid.toString(),
                    listingId
                )) {
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                connection.commit();
                return Optional.of(listing);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> release(String listingId) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_auction_listings SET status = 'ACTIVE', buyer_uuid = NULL WHERE id = ? AND status = 'RESERVED'",
                listingId
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> markSold(String listingId) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_auction_listings SET status = 'SOLD', claimed_at = ? WHERE id = ?",
                System.currentTimeMillis(),
                listingId
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> addMailboxDelivery(UUID playerUuid, ItemStack itemStack) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    INSERT INTO fx_mailbox_deliveries (id, player_uuid, type, payload_base64, status, created_at, claimed_at)
                    VALUES (?, ?, 'ITEM', ?, 'PENDING', ?, 0)
                    """,
                UUID.randomUUID().toString(),
                playerUuid.toString(),
                ItemStackSerializer.serialize(itemStack),
                System.currentTimeMillis()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<MailboxDelivery>> mailbox(UUID playerUuid) {
        return database.query(connection -> {
            List<MailboxDelivery> deliveries = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_mailbox_deliveries WHERE player_uuid = ? AND status = 'PENDING' ORDER BY created_at ASC",
                playerUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    deliveries.add(new MailboxDelivery(
                        resultSet.getString("id"),
                        UUID.fromString(resultSet.getString("player_uuid")),
                        ItemStackSerializer.deserialize(resultSet.getString("payload_base64"))
                    ));
                }
            }
            return deliveries;
        });
    }

    public CompletableFuture<Void> markMailboxClaimed(String deliveryId) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_mailbox_deliveries SET status = 'CLAIMED', claimed_at = ? WHERE id = ?",
                System.currentTimeMillis(),
                deliveryId
            )) {
                statement.executeUpdate();
            }
        });
    }

    private AuctionListing read(ResultSet resultSet) throws Exception {
        return new AuctionListing(
            resultSet.getString("id"),
            UUID.fromString(resultSet.getString("seller_uuid")),
            resultSet.getString("buyer_uuid") == null ? null : UUID.fromString(resultSet.getString("buyer_uuid")),
            ItemStackSerializer.deserialize(resultSet.getString("item_base64")),
            resultSet.getString("fingerprint"),
            resultSet.getBigDecimal("price"),
            resultSet.getBigDecimal("fee"),
            resultSet.getString("status"),
            resultSet.getLong("expires_at"),
            resultSet.getLong("created_at")
        );
    }

    public record MailboxDelivery(String id, UUID playerUuid, ItemStack itemStack) {
    }
}
