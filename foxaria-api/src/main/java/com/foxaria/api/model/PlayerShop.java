package com.foxaria.api.model;

import java.util.UUID;

public record PlayerShop(
    String id,
    UUID ownerUuid,
    String name,
    String description,
    boolean open,
    double taxPercent,
    long createdAt
) {
}
