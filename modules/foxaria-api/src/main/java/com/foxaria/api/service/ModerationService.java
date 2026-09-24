package com.foxaria.api.service;

import com.foxaria.api.model.PunishmentRecord;
import com.foxaria.api.model.StaffCheckSession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ModerationService {

    default CompletableFuture<Void> punish(CommandSender actor, UUID targetUuid, String type, String reason, long expiresAt) {
        return punish(actor, targetUuid, type, reason, expiresAt, false);
    }

    CompletableFuture<Void> punish(CommandSender actor, UUID targetUuid, String type, String reason, long expiresAt, boolean silent);

    default CompletableFuture<Void> revoke(CommandSender actor, UUID targetUuid, String reason, String... types) {
        return revoke(actor, targetUuid, reason, false, types);
    }

    CompletableFuture<Void> revoke(CommandSender actor, UUID targetUuid, String reason, boolean silent, String... types);

    CompletableFuture<Void> startCheck(CommandSender actor, Player target, boolean silent);

    CompletableFuture<Void> finishCheck(CommandSender actor, UUID targetUuid, boolean silent, String reason);

    boolean isUnderCheck(UUID playerUuid);

    List<StaffCheckSession> activeChecks();

    CompletableFuture<List<PunishmentRecord>> history(UUID targetUuid);

    boolean isMuted(UUID playerUuid);

    boolean isFrozen(UUID playerUuid);
}
