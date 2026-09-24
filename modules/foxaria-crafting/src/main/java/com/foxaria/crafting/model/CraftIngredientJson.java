package com.foxaria.crafting.model;

/**
 * Один слот сетки 3×3: шаблон + количество или сериализованный стак (с количеством).
 */
public final class CraftIngredientJson {
    public String templateId;
    public String stackBase64;
    public int amount = 1;
}
