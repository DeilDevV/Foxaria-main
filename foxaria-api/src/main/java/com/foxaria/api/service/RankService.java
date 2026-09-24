package com.foxaria.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RankService {

    CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String group);

    CompletableFuture<Void> grantTemporaryGroup(UUID playerUuid, String group, long durationSeconds);

    CompletableFuture<Void> removeGroup(UUID playerUuid, String group);

    CompletableFuture<Void> grantTemporaryPermission(UUID playerUuid, String permission, long durationSeconds, String reason);

    CompletableFuture<String> primaryGroup(UUID playerUuid);
}
