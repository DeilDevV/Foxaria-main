package com.foxaria.api.model;

import java.util.UUID;

public record PunishmentRecord(
    String id,
    UUID targetUuid,
    UUID actorUuid,
    String type,
    String reason,
    long createdAt,
    long expiresAt,
    boolean active
) {
}
