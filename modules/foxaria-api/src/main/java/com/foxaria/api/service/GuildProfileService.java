package com.foxaria.api.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildProfileService {

    CompletableFuture<Optional<String>> guildNameOf(UUID playerUuid);
}
