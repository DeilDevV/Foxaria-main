package com.foxaria.api.service;

import com.foxaria.api.model.PlayerShop;
import com.foxaria.api.model.PlayerShopOffer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PlayerShopService {

    CompletableFuture<PlayerShop> ensureShop(Player owner);

    CompletableFuture<List<PlayerShop>> listShops();

    CompletableFuture<List<PlayerShopOffer>> offers(String shopId);

    CompletableFuture<Void> listOffer(Player owner, ItemStack itemStack, BigDecimal price);

    CompletableFuture<Void> buy(Player buyer, String offerId);
}
