package com.foxaria.api.model;

import java.util.UUID;

public record SecurityIncident(
    String id,
    UUID actorUuid,
    String type,
    String summary,
    long createdAt
) {
}
