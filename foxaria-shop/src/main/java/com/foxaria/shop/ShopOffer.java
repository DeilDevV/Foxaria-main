package com.foxaria.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

public record ShopOffer(
    String id,
    String category,
    String displayName,
    Material material,
    int amount,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    String itemTemplateId
) {
    public boolean usesTemplate() {
        return itemTemplateId != null && !itemTemplateId.isBlank();
    }

    public ItemStack icon() {
        return new ItemStack(material, amount);
    }
}
