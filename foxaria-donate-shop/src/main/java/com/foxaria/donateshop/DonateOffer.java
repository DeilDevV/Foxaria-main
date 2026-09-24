package com.foxaria.donateshop;

import org.bukkit.inventory.ItemStack;

public record DonateOffer(
    String id,
    DonateCategory category,
    long priceTokens,
    ItemStack displayItem,
    int sortOrder
) {
}
