package com.foxaria.api.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public interface ShopService {

    void open(Player player);

    CompletableFuture<Void> buy(Player player, String offerId, int amount);

    CompletableFuture<Void> sell(Player player, ItemStack itemStack, BigDecimal price);
}
