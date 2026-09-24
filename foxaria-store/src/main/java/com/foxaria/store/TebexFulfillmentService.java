package com.foxaria.store;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.RankService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TebexFulfillmentService {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final TebexFulfillmentRepository repository;
    private final EconomyService economyService;
    private final RankService rankService;
    private final AuditService audits;

    public TebexFulfillmentService(JavaPlugin plugin, FileConfiguration config, TebexFulfillmentRepository repository, EconomyService economyService, RankService rankService, AuditService audits) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.economyService = economyService;
        this.rankService = rankService;
        this.audits = audits;
    }

    public void enqueue(String externalTxnId, UUID playerUuid, String packageId) {
        enqueueLifecycle(externalTxnId, playerUuid, packageId, false);
    }

    public void enqueueRevoke(String externalTxnId, UUID playerUuid, String packageId) {
        enqueueLifecycle(externalTxnId, playerUuid, packageId, true);
    }

    public void start() {
        long interval = config.getLong("processing-interval-ticks", 100L);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::processPending, interval, interval);
    }

    private void enqueueLifecycle(String externalTxnId, UUID playerUuid, String packageId, boolean revoke) {
        String path = "packages." + packageId + "." + (revoke ? "revoke-action" : "action");
        String action = config.getString(path, "");
        String lifecycleTxnId = externalTxnId + (revoke ? "#revoke" : "#grant");
        repository.enqueue(lifecycleTxnId, playerUuid, packageId, action, "{\"phase\":\"" + (revoke ? "REVOKE" : "GRANT") + "\"}");
    }

    private void processPending() {
        repository.pending(config.getInt("batch-size", 10)).thenAccept(requests -> requests.forEach(request ->
            repository.claimForProcessing(request.id()).thenAccept(claimed -> {
                if (!claimed) {
                    return;
                }
                execute(request).whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        String error = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
                        repository.markFailed(request.id(), error);
                        audits.append(new AuditEvent(
                            "TEBEX_FULFILLMENT_RETRY",
                            request.playerUuid(),
                            request.playerUuid(),
                            request.playerUuid().toString(),
                            request.playerUuid().toString(),
                            "Tebex package failed and queued for retry",
                            Map.of("packageId", request.packageId(), "error", error),
                            System.currentTimeMillis()
                        ));
                        return;
                    }
                    repository.markFulfilled(request.id());
                    audits.append(new AuditEvent(
                        "TEBEX_FULFILLMENT_SUCCESS",
                        request.playerUuid(),
                        request.playerUuid(),
                        request.playerUuid().toString(),
                        request.playerUuid().toString(),
                        "Tebex package fulfilled",
                        Map.of("packageId", request.packageId(), "externalTxnId", request.externalTxnId()),
                        System.currentTimeMillis()
                    ));
                });
            })
        ));
    }

    private CompletableFuture<Void> execute(TebexFulfillmentRepository.TebexRequest request) {
        String action = request.action();
        if (action == null || action.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No action configured for package " + request.packageId()));
        }
        String[] parts = action.split(":", 3);
        return switch (parts[0]) {
            case "rank" -> rankService.setPrimaryGroup(request.playerUuid(), parts[1]);
            case "temp_rank" -> rankService.grantTemporaryGroup(request.playerUuid(), parts[1], Long.parseLong(parts[2]));
            case "subscription" -> rankService.grantTemporaryGroup(request.playerUuid(), parts[1], Long.parseLong(parts[2]))
                .thenCompose(ignored -> repository.upsertSubscription(
                    baseTxnId(request.externalTxnId()),
                    request.playerUuid(),
                    request.packageId(),
                    parts[1],
                    System.currentTimeMillis() + (Long.parseLong(parts[2]) * 1000L),
                    "ACTIVE"
                ));
            case "remove_rank" -> rankService.removeGroup(request.playerUuid(), parts[1])
                .thenCompose(ignored -> repository.updateSubscriptionStatus(baseTxnId(request.externalTxnId()), "REVOKED"));
            case "coins" -> economyService.deposit(request.playerUuid(), new BigDecimal(parts[1]), "tebex_package:" + request.packageId(), null);
            case "take_coins" -> economyService.withdraw(request.playerUuid(), new BigDecimal(parts[1]), "tebex_revoke:" + request.packageId(), null);
            case "command" -> dispatchConsole(action.substring("command:".length()), request.playerUuid());
            case "command_batch" -> dispatchBatch(parts.length > 1 ? action.substring("command_batch:".length()) : "");
            default -> CompletableFuture.failedFuture(new IllegalStateException("Unsupported Tebex action: " + action));
        };
    }

    private CompletableFuture<Void> dispatchBatch(String payload) {
        String[] commands = payload.split("\\|\\|");
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String command : commands) {
            String trimmed = command.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            chain = chain.thenCompose(ignored -> runSync(() ->
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), trimmed)
            ));
        }
        return chain;
    }

    private CompletableFuture<Void> dispatchConsole(String rawCommand, UUID playerUuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        String command = rawCommand.replace("%player%", player.getName() == null ? playerUuid.toString() : player.getName());
        return runSync(() -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command));
    }

    private CompletableFuture<Void> runSync(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private String baseTxnId(String externalTxnId) {
        int index = externalTxnId.indexOf('#');
        return index >= 0 ? externalTxnId.substring(0, index) : externalTxnId;
    }
}
