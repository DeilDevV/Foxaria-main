package com.foxaria.api.service;

import com.foxaria.api.model.SecurityIncident;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SecurityService {

    boolean allowCommand(CommandSender sender, String commandKey);

    CompletableFuture<Void> recordEvent(UUID actorUuid, String type, String summary);

    CompletableFuture<List<SecurityIncident>> recent(UUID actorUuid, int limit);
}
