package com.foxaria.modernfurnace;

import org.bukkit.Bukkit;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;

import java.util.Iterator;

/**
 * Поиск рецепта плавки (печь / плавильная / коптильня). Для одного входа приоритет: плавильная → обычная → коптильня.
 */
public final class SmeltingUtil {

    private SmeltingUtil() {
    }

    public static CookingRecipe<?> findCookingRecipe(ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return null;
        }
        ItemStack probe = input.clone();
        probe.setAmount(1);
        CookingRecipe<?> blast = null;
        CookingRecipe<?> furnace = null;
        CookingRecipe<?> smoke = null;
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (!(recipe instanceof CookingRecipe<?> cr)) {
                continue;
            }
            RecipeChoice choice = cr.getInputChoice();
            if (choice == null || !choice.test(probe)) {
                continue;
            }
            if (recipe instanceof BlastingRecipe) {
                blast = pickBetter(blast, cr);
            } else if (recipe instanceof FurnaceRecipe) {
                furnace = pickBetter(furnace, cr);
            } else if (recipe instanceof SmokingRecipe) {
                smoke = pickBetter(smoke, cr);
            }
        }
        if (blast != null) {
            return blast;
        }
        if (furnace != null) {
            return furnace;
        }
        return smoke;
    }

    private static CookingRecipe<?> pickBetter(CookingRecipe<?> current, CookingRecipe<?> candidate) {
        return current == null ? candidate : current;
    }

    public static int baseCookTicks(CookingRecipe<?> recipe) {
        int t = recipe.getCookingTime();
        return t > 0 ? t : 200;
    }
}
