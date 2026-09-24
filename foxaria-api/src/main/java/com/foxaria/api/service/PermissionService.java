package com.foxaria.api.service;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PermissionService {

    boolean has(CommandSender sender, String permission);

    CompletableFuture<Void> grantTemporary(UUID playerUuid, String permission, long durationSeconds, String reason);

    CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String groupName);

    CompletableFuture<String> primaryGroup(Player player);
}
