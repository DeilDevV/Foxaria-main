package com.foxaria.shop.progression;

import java.math.BigDecimal;

public record ProgressionShopOffer(
    String id,
    String category,
    int requiredKnowledge,
    BigDecimal price,
    String itemBlob,
    String itemTemplate,
    int sortOrder
) {
    public boolean usesTemplate() {
        return itemTemplate != null && !itemTemplate.isBlank();
    }
}
