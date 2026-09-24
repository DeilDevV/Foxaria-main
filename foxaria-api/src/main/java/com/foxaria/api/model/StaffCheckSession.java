package com.foxaria.api.model;

import java.util.UUID;

public record StaffCheckSession(
    UUID targetUuid,
    UUID actorUuid,
    String actorName,
    long startedAt,
    boolean silent
) {
}
