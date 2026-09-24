package com.foxaria.crafting;

import com.foxaria.crafting.model.CraftRecipeJson;

public record CustomCraftDefinition(String craftId, String recipeJson, int sortOrder) {
    public CraftRecipeJson parsed() {
        return CraftJson.parse(recipeJson);
    }
}
