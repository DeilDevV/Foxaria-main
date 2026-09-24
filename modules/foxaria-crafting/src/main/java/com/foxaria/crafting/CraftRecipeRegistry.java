package com.foxaria.crafting;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.crafting.model.CraftRecipeJson;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CraftRecipeRegistry {

    private final CopyOnWriteArrayList<NamespacedKey> registered = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<NamespacedKey, String> keyToCraftId = new ConcurrentHashMap<>();

    public void reload(JavaPlugin plugin, ItemTemplateService templates, List<CustomCraftDefinition> defs) {
        clear(plugin);
        for (CustomCraftDefinition def : defs) {
            try {
                CraftRecipeJson json = def.parsed();
                if (CraftJson.isTable(json)) {
                    NamespacedKey key = keyFor(plugin, def.craftId());
                    ItemStack res = CraftJson.resolveResult(templates, json).clone();
                    ItemStack[] g = CraftJson.resolveGrid(templates, json);
                    ShapedRecipe recipe = buildShaped(key, res, g);
                    Bukkit.addRecipe(recipe);
                    registered.add(key);
                    keyToCraftId.put(key, def.craftId());
                } else if (CraftJson.isSmeltingKind(json) && json.registerPrimaryRecipe) {
                    ItemStack res = CraftJson.resolveResult(templates, json).clone();
                    ItemStack in = firstInputExact(templates, json);
                    if (in == null || in.getType().isAir()) {
                        throw new IllegalStateException("Нет входа для печи");
                    }
                    RecipeChoice choice = new RecipeChoice.ExactChoice(in.clone());
                    int ticks = json.smeltCookTicks;
                    float xp = json.smeltExperience;
                    NamespacedKey keyF = smeltFurnaceKey(plugin, def.craftId());
                    FurnaceRecipe fr = new FurnaceRecipe(keyF, res.clone(), choice, xp, ticks);
                    Bukkit.addRecipe(fr);
                    registered.add(keyF);
                    keyToCraftId.put(keyF, def.craftId());
                    NamespacedKey keyB = smeltBlastKey(plugin, def.craftId());
                    BlastingRecipe br = new BlastingRecipe(keyB, res.clone(), choice, xp, ticks);
                    Bukkit.addRecipe(br);
                    registered.add(keyB);
                    keyToCraftId.put(keyB, def.craftId());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("[Craft] Не удалось зарегистрировать " + def.craftId() + ": " + ex.getMessage());
            }
        }
    }

    private static ItemStack firstInputExact(ItemTemplateService templates, CraftRecipeJson json) {
        for (ItemStack s : CraftJson.resolveGrid(templates, json)) {
            if (s != null && !s.getType().isAir()) {
                return s.clone();
            }
        }
        return null;
    }

    private static ShapedRecipe buildShaped(NamespacedKey key, ItemStack result, ItemStack[] grid9) {
        int sym = 0;
        String[] rows = new String[3];
        Map<Character, RecipeChoice> choices = new HashMap<>();
        for (int r = 0; r < 3; r++) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                ItemStack cell = grid9[i];
                if (cell == null || cell.getType().isAir()) {
                    line.append(' ');
                } else {
                    char ch = (char) ('A' + sym);
                    if (sym >= 9) {
                        throw new IllegalStateException("Слишком много непустых слотов в рецепте");
                    }
                    sym++;
                    line.append(ch);
                    choices.put(ch, new RecipeChoice.ExactChoice(cell.clone()));
                }
            }
            rows[r] = line.toString();
        }
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(rows[0], rows[1], rows[2]);
        for (Map.Entry<Character, RecipeChoice> e : choices.entrySet()) {
            recipe.setIngredient(e.getKey(), e.getValue());
        }
        return recipe;
    }

    public static NamespacedKey keyFor(JavaPlugin plugin, String craftId) {
        String safe = craftId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return new NamespacedKey(plugin, "craft_" + safe);
    }

    private static NamespacedKey smeltFurnaceKey(JavaPlugin plugin, String craftId) {
        String safe = craftId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return new NamespacedKey(plugin, "craft_" + safe + "_furnace");
    }

    private static NamespacedKey smeltBlastKey(JavaPlugin plugin, String craftId) {
        String safe = craftId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return new NamespacedKey(plugin, "craft_" + safe + "_blast");
    }

    public void clear(JavaPlugin plugin) {
        List<NamespacedKey> copy = new ArrayList<>(registered);
        for (NamespacedKey key : copy) {
            Bukkit.removeRecipe(key);
            keyToCraftId.remove(key);
        }
        registered.clear();
    }

    public String resolveCraftId(Keyed keyed) {
        return keyToCraftId.get(keyed.getKey());
    }

    public static boolean isFoxariaCraftRecipe(Recipe recipe, JavaPlugin plugin) {
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }
        if (!keyed.getKey().getKey().startsWith("craft_")) {
            return false;
        }
        return keyed.getKey().getNamespace().equalsIgnoreCase(plugin.getName());
    }

    public static boolean isFoxariaCraftRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }
        return keyed.getKey().getKey().startsWith("craft_");
    }
}
