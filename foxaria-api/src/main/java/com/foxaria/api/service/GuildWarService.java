package com.foxaria.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildWarService {

    CompletableFuture<Void> inviteWar(String challengerGuildId, String targetGuildId, int teamSize, UUID initiator);

    CompletableFuture<Void> queueGuild(String guildId, int teamSize, UUID initiator);

    CompletableFuture<Void> leaveQueue(String guildId);
}
