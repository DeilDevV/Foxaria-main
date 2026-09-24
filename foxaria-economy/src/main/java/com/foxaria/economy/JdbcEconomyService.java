package com.foxaria.economy;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.model.BalanceSnapshot;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdbcEconomyService implements EconomyService {

    private final EconomyRepository repository;
    private final AuditService audits;

    public JdbcEconomyService(EconomyRepository repository, AuditService audits) {
        this.repository = repository;
        this.audits = audits;
    }

    @Override
    public CompletableFuture<BalanceSnapshot> balance(UUID playerUuid) {
        return repository.ensureAccount(playerUuid).thenCompose(ignored -> repository.balance(playerUuid));
    }

    @Override
    public CompletableFuture<Void> deposit(UUID playerUuid, BigDecimal amount, String reason, UUID actorUuid) {
        String txId = UUID.randomUUID().toString();
        return repository.deposit(playerUuid, amount, txId, reason, actorUuid)
            .thenRun(() -> audits.append(new AuditEvent(
                "ECONOMY_DEPOSIT",
                actorUuid,
                playerUuid,
                actorUuid == null ? "system" : actorUuid.toString(),
                playerUuid.toString(),
                "Balance deposited",
                Map.of("amount", amount.toPlainString(), "reason", reason),
                System.currentTimeMillis()
            )));
    }

    @Override
    public CompletableFuture<Void> withdraw(UUID playerUuid, BigDecimal amount, String reason, UUID actorUuid) {
        String txId = UUID.randomUUID().toString();
        return repository.withdraw(playerUuid, amount, txId, reason, actorUuid)
            .thenRun(() -> audits.append(new AuditEvent(
                "ECONOMY_WITHDRAW",
                actorUuid,
                playerUuid,
                actorUuid == null ? "system" : actorUuid.toString(),
                playerUuid.toString(),
                "Balance withdrawn",
                Map.of("amount", amount.toPlainString(), "reason", reason),
                System.currentTimeMillis()
            )));
    }

    @Override
    public CompletableFuture<Void> transfer(UUID from, UUID to, BigDecimal amount, BigDecimal fee, String reason) {
        String txId = UUID.randomUUID().toString();
        return repository.transfer(from, to, amount, fee, txId, reason)
            .thenRun(() -> audits.append(new AuditEvent(
                "ECONOMY_PAY",
                from,
                to,
                from.toString(),
                to.toString(),
                "Player transfer",
                Map.of("amount", amount.toPlainString(), "fee", fee.toPlainString(), "reason", reason),
                System.currentTimeMillis()
            )));
    }

    @Override
    public CompletableFuture<List<BalanceSnapshot>> top(int limit) {
        return repository.top(limit);
    }

    @Override
    public CompletableFuture<Void> ensureAccount(OfflinePlayer player) {
        return repository.ensureAccount(player.getUniqueId());
    }

    @Override
    public CompletableFuture<Void> depositTokens(UUID playerUuid, long amount, String reason, UUID actorUuid) {
        if (amount <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        String txId = UUID.randomUUID().toString();
        return repository.addTokens(playerUuid, amount, txId, reason, actorUuid)
            .thenRun(() -> audits.append(new AuditEvent(
                "ECONOMY_TOKENS_DEPOSIT",
                actorUuid,
                playerUuid,
                actorUuid == null ? "system" : actorUuid.toString(),
                playerUuid.toString(),
                "Tokens deposited",
                Map.of("amount", String.valueOf(amount), "reason", reason),
                System.currentTimeMillis()
            )));
    }

    @Override
    public CompletableFuture<Void> withdrawTokens(UUID playerUuid, long amount, String reason, UUID actorUuid) {
        if (amount <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        String txId = UUID.randomUUID().toString();
        return repository.removeTokens(playerUuid, amount, txId, reason, actorUuid)
            .thenRun(() -> audits.append(new AuditEvent(
                "ECONOMY_TOKENS_WITHDRAW",
                actorUuid,
                playerUuid,
                actorUuid == null ? "system" : actorUuid.toString(),
                playerUuid.toString(),
                "Tokens withdrawn",
                Map.of("amount", String.valueOf(amount), "reason", reason),
                System.currentTimeMillis()
            )));
    }
}
