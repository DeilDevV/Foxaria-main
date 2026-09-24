package com.foxaria.guilds;

import com.foxaria.api.service.GuildWarService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ActiveGuildWarService implements GuildWarService {

    private final GuildWarEngine engine;

    public ActiveGuildWarService(GuildWarEngine engine) {
        this.engine = engine;
    }

    @Override
    public CompletableFuture<Void> inviteWar(String challengerGuildId, String targetGuildId, int teamSize, UUID initiator) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> queueGuild(String guildId, int teamSize, UUID initiator) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> leaveQueue(String guildId) {
        return CompletableFuture.completedFuture(null);
    }
}
