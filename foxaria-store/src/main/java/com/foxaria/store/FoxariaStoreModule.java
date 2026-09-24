package com.foxaria.store;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.RankService;
import com.foxaria.store.command.StoreGrantCommand;
import org.bukkit.command.PluginCommand;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FoxariaStoreModule implements FoxariaModule {

    @Override
    public String id() {
        return "store";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(10, "store_base", "db/migration/V10__store_base.sql"),
            new MigrationScript(13, "store_lifecycle", "db/migration/V13__store_lifecycle.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/store.yml");
        var storeConfig = context.configs().module("modules/store.yml");
        if (!storeConfig.getBoolean("enabled", true)) {
            context.plugin().getLogger().info("Module store is disabled in modules/store.yml");
            return;
        }
        context.database().applyMigrations(migrations());
        TebexFulfillmentService service = new TebexFulfillmentService(
            context.plugin(),
            storeConfig,
            new TebexFulfillmentRepository(context.database()),
            context.services().require(EconomyService.class),
            rankServiceOrNoop(context),
            context.audits()
        );
        service.start();
        context.services().register(TebexFulfillmentService.class, service);

        if (storeConfig.getBoolean("tebex-queue.enabled", false) && !storeConfig.getString("tebex-queue.secret", "").isBlank()) {
            TebexApiClient client = new TebexApiClient(
                storeConfig.getString("tebex-queue.base-url", "https://plugin.tebex.io"),
                storeConfig.getString("tebex-queue.secret", "")
            );
            TebexQueuePoller poller = new TebexQueuePoller(context.plugin(), client, context.audits());
            poller.start(20L);
            context.services().register(TebexQueuePoller.class, poller);
        }

        PluginCommand command = context.plugin().getCommand("storegrant");
        if (command != null) {
            command.setExecutor(new StoreGrantCommand(service, context.messages()));
        }
    }

    @Override
    public void stop() {
    }

    private static RankService rankServiceOrNoop(ModuleContext context) {
        RankService rankService = context.services().optional(RankService.class);
        if (rankService != null) {
            return rankService;
        }
        context.plugin().getLogger().warning("RankService is not registered. Rank Tebex actions are disabled on this backend.");
        return new RankService() {
            @Override
            public CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String group) {
                return CompletableFuture.failedFuture(new IllegalStateException("RankService unavailable on backend"));
            }

            @Override
            public CompletableFuture<Void> grantTemporaryGroup(UUID playerUuid, String group, long durationSeconds) {
                return CompletableFuture.failedFuture(new IllegalStateException("RankService unavailable on backend"));
            }

            @Override
            public CompletableFuture<Void> removeGroup(UUID playerUuid, String group) {
                return CompletableFuture.failedFuture(new IllegalStateException("RankService unavailable on backend"));
            }

            @Override
            public CompletableFuture<Void> grantTemporaryPermission(UUID playerUuid, String permission, long durationSeconds, String reason) {
                return CompletableFuture.failedFuture(new IllegalStateException("RankService unavailable on backend"));
            }

            @Override
            public CompletableFuture<String> primaryGroup(UUID playerUuid) {
                return CompletableFuture.completedFuture("default");
            }
        };
    }
}
