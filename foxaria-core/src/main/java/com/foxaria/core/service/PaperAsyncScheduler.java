package com.foxaria.core.service;

import com.foxaria.api.service.AsyncScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class PaperAsyncScheduler implements AsyncScheduler {

    private final JavaPlugin plugin;

    public PaperAsyncScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(ThrowingSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    @Override
    public void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public void runLaterSync(Runnable task, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}
