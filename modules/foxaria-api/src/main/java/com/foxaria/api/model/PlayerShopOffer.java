package com.foxaria.api.model;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

public record PlayerShopOffer(
    String id,
    String shopId,
    ItemStack item,
    String fingerprint,
    BigDecimal price,
    int stock,
    boolean active,
    long createdAt
) {
}
