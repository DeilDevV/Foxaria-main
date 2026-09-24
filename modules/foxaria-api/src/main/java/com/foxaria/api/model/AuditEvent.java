package com.foxaria.api.model;

import java.util.Map;
import java.util.UUID;

public record AuditEvent(
    String type,
    UUID actorUuid,
    UUID targetUuid,
    String actorName,
    String targetName,
    String summary,
    Map<String, String> metadata,
    long createdAt
) {
}
