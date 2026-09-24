package com.foxaria.api.model;

import org.bukkit.inventory.ItemStack;

/** Один предмет в почте аукциона, ожидающий выдачи. */
public record AuctionMailboxItem(String deliveryId, ItemStack item) {
}
