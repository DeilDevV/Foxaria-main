package com.foxaria.core.service;

import com.foxaria.api.service.RankService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * RankService для scoreboard/магазинов: группа из той же SQLite, что и FoxariaPermissionService.
 */
public final class PermissionBackedRankService implements RankService {

    private final FoxariaPermissionService permissions;

    public PermissionBackedRankService(FoxariaPermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String group) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> grantTemporaryGroup(UUID playerUuid, String group, long durationSeconds) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeGroup(UUID playerUuid, String group) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> grantTemporaryPermission(UUID playerUuid, String permission, long durationSeconds, String reason) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> primaryGroup(UUID playerUuid) {
        return CompletableFuture.completedFuture(permissions.resolveGroupFromDatabase(playerUuid));
    }
}
