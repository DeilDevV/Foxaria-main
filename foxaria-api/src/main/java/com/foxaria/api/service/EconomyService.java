package com.foxaria.api.service;

import com.foxaria.api.model.BalanceSnapshot;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyService {

    CompletableFuture<BalanceSnapshot> balance(UUID playerUuid);

    CompletableFuture<Void> deposit(UUID playerUuid, BigDecimal amount, String reason, UUID actorUuid);

    CompletableFuture<Void> withdraw(UUID playerUuid, BigDecimal amount, String reason, UUID actorUuid);

    CompletableFuture<Void> transfer(UUID from, UUID to, BigDecimal amount, BigDecimal fee, String reason);

    CompletableFuture<List<BalanceSnapshot>> top(int limit);

    CompletableFuture<Void> ensureAccount(OfflinePlayer player);

    CompletableFuture<Void> depositTokens(UUID playerUuid, long amount, String reason, UUID actorUuid);

    CompletableFuture<Void> withdrawTokens(UUID playerUuid, long amount, String reason, UUID actorUuid);
}
