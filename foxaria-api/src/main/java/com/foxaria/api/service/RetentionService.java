package com.foxaria.api.service;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public interface RetentionService {

    CompletableFuture<Void> onJoin(Player player);

    CompletableFuture<Integer> streak(Player player);
}
