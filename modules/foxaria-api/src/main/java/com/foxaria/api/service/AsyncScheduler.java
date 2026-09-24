package com.foxaria.api.service;

import java.util.concurrent.CompletableFuture;

public interface AsyncScheduler {

    CompletableFuture<Void> runAsync(Runnable task);

    <T> CompletableFuture<T> supplyAsync(ThrowingSupplier<T> supplier);

    void runSync(Runnable task);

    void runLaterSync(Runnable task, long delayTicks);

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
