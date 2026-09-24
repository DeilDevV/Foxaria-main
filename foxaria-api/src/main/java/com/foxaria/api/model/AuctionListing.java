package com.foxaria.api.model;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.UUID;

public record AuctionListing(
    String id,
    UUID sellerUuid,
    UUID buyerUuid,
    ItemStack item,
    String fingerprint,
    BigDecimal price,
    BigDecimal fee,
    String status,
    long expiresAt,
    long createdAt
) {
}
