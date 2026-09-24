package com.foxaria.api.service;

import com.foxaria.api.model.AuctionListing;
import com.foxaria.api.model.AuctionMailboxItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuctionService {

    CompletableFuture<List<AuctionListing>> activeListings();

    CompletableFuture<Void> listItem(Player seller, ItemStack item, BigDecimal price);

    CompletableFuture<Void> buy(Player buyer, String listingId);

    CompletableFuture<Void> openMailbox(Player player);

    CompletableFuture<List<AuctionMailboxItem>> pendingMailboxItems(UUID playerUuid);

    CompletableFuture<Boolean> claimMailboxDelivery(Player player, String deliveryId);
}
