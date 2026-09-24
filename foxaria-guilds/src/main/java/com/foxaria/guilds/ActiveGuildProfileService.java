package com.foxaria.guilds;

import com.foxaria.api.service.GuildProfileService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ActiveGuildProfileService implements GuildProfileService {

    private final GuildRepository repository;
    private final GuildService guilds;

    public ActiveGuildProfileService(GuildRepository repository, GuildService guilds) {
        this.repository = repository;
        this.guilds = guilds;
    }

    @Override
    public CompletableFuture<Optional<String>> guildNameOf(UUID playerUuid) {
        return repository.byPlayer(playerUuid).thenApply(opt -> opt.map(g -> {
            String color = guilds == null ? "&f" : guilds.legacyColorForGuildTag(g.tagColor());
            if (color == null || color.isBlank()) {
                color = "&f";
            }
            return color + g.name();
        }));
    }
}
