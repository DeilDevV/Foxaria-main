package com.foxaria.api.service;

import com.foxaria.api.model.AuditEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuditService {

    CompletableFuture<Void> append(AuditEvent event);

    CompletableFuture<List<AuditEvent>> recent(UUID targetUuid, int limit);
}
