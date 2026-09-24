package com.foxaria.api.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceSnapshot(
    UUID playerUuid,
    BigDecimal balance,
    long tokens,
    long updatedAt
) {
}
