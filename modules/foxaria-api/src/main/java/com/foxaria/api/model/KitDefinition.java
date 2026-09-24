package com.foxaria.api.model;

public record KitDefinition(
    String id,
    String displayName,
    long cooldownSeconds,
    String permission,
    long requiredPlaytimeSeconds,
    KitContents contents
) {
}
