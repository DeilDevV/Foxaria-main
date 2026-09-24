package com.foxaria.crafting;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.core.util.ItemStackSerializer;
import com.foxaria.crafting.model.CraftIngredientJson;
import com.foxaria.crafting.model.CraftRecipeJson;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CraftJson {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private CraftJson() {
    }

    public static CraftRecipeJson parse(String json) {
        CraftRecipeJson r = GSON.fromJson(json, CraftRecipeJson.class);
        if (r == null) {
            return normalize(new CraftRecipeJson());
        }
        return normalize(r);
    }

    /**
     * Приводит старые рецепты к сетке 9 слотов (ингредиенты по порядку в первые ячейки).
     */
    public static CraftRecipeJson normalize(CraftRecipeJson j) {
        if (j == null) {
            return new CraftRecipeJson();
        }
        boolean hasGrid = j.grid != null && j.grid.length == 9;
        boolean gridEmpty = true;
        if (hasGrid) {
            for (CraftIngredientJson c : j.grid) {
                if (c != null && !isEmptySlot(c)) {
                    gridEmpty = false;
                    break;
                }
            }
        }
        if (!hasGrid || gridEmpty) {
            if (j.ingredients != null && !j.ingredients.isEmpty()) {
                j.grid = new CraftIngredientJson[9];
                int p = 0;
                for (CraftIngredientJson ing : j.ingredients) {
                    if (ing == null || isEmptySlot(ing)) {
                        continue;
                    }
                    while (p < 9 && j.grid[p] != null) {
                        p++;
                    }
                    if (p >= 9) {
                        break;
                    }
                    j.grid[p++] = ing;
                }
            } else if (!hasGrid || j.grid == null || j.grid.length != 9) {
                j.grid = new CraftIngredientJson[9];
            }
        }
        if (j.grid != null && j.grid.length != 9) {
            CraftIngredientJson[] fix = new CraftIngredientJson[9];
            System.arraycopy(j.grid, 0, fix, 0, Math.min(9, j.grid.length));
            j.grid = fix;
        }
        if (j.creationKind == null || j.creationKind.isBlank()) {
            j.creationKind = CraftCreationKinds.TABLE;
        }
        j.creationKind = j.creationKind.trim().toUpperCase(java.util.Locale.ROOT);
        if (CraftCreationKinds.BLAST_FURNACE.equalsIgnoreCase(j.creationKind)) {
            j.creationKind = CraftCreationKinds.FURNACE;
        }
        if (j.bonusChancePercent < 0) {
            j.bonusChancePercent = 0;
        }
        if (j.bonusChancePercent > 100) {
            j.bonusChancePercent = 100;
        }
        if (j.smeltCookTicks <= 0) {
            j.smeltCookTicks = 200;
        }
        // Старые JSON без поля: Gson даёт false — для верстака рецепт должен регистрироваться.
        if (CraftCreationKinds.TABLE.equalsIgnoreCase(j.creationKind)) {
            j.registerPrimaryRecipe = true;
        }
        return j;
    }

    public static boolean isTable(CraftRecipeJson j) {
        normalize(j);
        return CraftCreationKinds.TABLE.equalsIgnoreCase(j.creationKind);
    }

    public static boolean isFurnace(CraftRecipeJson j) {
        normalize(j);
        return CraftCreationKinds.FURNACE.equalsIgnoreCase(j.creationKind);
    }

    public static boolean isBlastFurnace(CraftRecipeJson j) {
        normalize(j);
        return CraftCreationKinds.BLAST_FURNACE.equalsIgnoreCase(j.creationKind);
    }

    public static boolean isSmeltingKind(CraftRecipeJson j) {
        return isFurnace(j) || isBlastFurnace(j);
    }

    /** Подпись для /craft: верстак или печи. */
    public static String[] creationLoreLines(CraftRecipeJson j) {
        normalize(j);
        if (isTable(j)) {
            return new String[]{"&7Создание: в верстаке"};
        }
        if (isSmeltingKind(j)) {
            return new String[]{
                "&7Создание: в печи",
                "&8(&7Все печи: обычная, плавильная, модерн&8)"
            };
        }
        return new String[]{"&7Создание: в верстаке"};
    }

    /** Первая непустая ячейка сетки как вход печи (1×1). */
    public static org.bukkit.Material firstSmeltInputMaterial(ItemTemplateService templates, CraftRecipeJson json) {
        for (ItemStack s : resolveGrid(templates, json)) {
            if (s != null && !s.getType().isAir()) {
                return s.getType();
            }
        }
        return null;
    }

    public static CraftRecipeJson encodeSmelt(
        ItemStack[] grid9,
        ItemStack result,
        int knowledge,
        String displayName,
        String creationKind,
        int smeltCookTicks,
        float smeltExperience,
        boolean registerPrimaryRecipe,
        String bonusTemplateId,
        double bonusChancePercent
    ) {
        CraftRecipeJson out = encodeGrid(grid9, result, knowledge, displayName);
        out.creationKind = creationKind == null ? CraftCreationKinds.FURNACE : creationKind.trim().toUpperCase(java.util.Locale.ROOT);
        out.smeltCookTicks = Math.max(1, smeltCookTicks);
        out.smeltExperience = smeltExperience;
        out.registerPrimaryRecipe = registerPrimaryRecipe;
        out.bonusTemplateId = bonusTemplateId == null ? "" : bonusTemplateId.trim();
        out.bonusChancePercent = Math.max(0, Math.min(100, bonusChancePercent));
        return normalize(out);
    }

    private static boolean isEmptySlot(CraftIngredientJson c) {
        if (c == null) {
            return true;
        }
        boolean hasT = c.templateId != null && !c.templateId.isBlank();
        boolean hasB = c.stackBase64 != null && !c.stackBase64.isBlank();
        return !hasT && !hasB;
    }

    public static String serialize(CraftRecipeJson payload) {
        return GSON.toJson(payload);
    }

    public static CraftRecipeJson encodeGrid(ItemStack[] grid9, ItemStack result, int knowledge, String displayName) {
        CraftRecipeJson out = new CraftRecipeJson();
        out.requiredKnowledge = Math.max(1, knowledge);
        if (result == null || result.getType().isAir()) {
            throw new IllegalArgumentException("Пустой результат");
        }
        out.resultStackBase64 = ItemStackSerializer.serialize(result.clone());
        out.resultTemplateId = "";
        out.resultAmount = Math.max(1, Math.min(64, result.getAmount()));
        out.displayName = (displayName == null || displayName.isBlank()) ? "&fКрафт" : displayName;
        out.grid = new CraftIngredientJson[9];
        out.ingredients = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            ItemStack cell = grid9[i];
            if (cell == null || cell.getType().isAir()) {
                out.grid[i] = null;
                continue;
            }
            any = true;
            CraftIngredientJson ing = new CraftIngredientJson();
            ing.amount = Math.max(1, Math.min(64, cell.getAmount()));
            ing.templateId = null;
            ing.stackBase64 = ItemStackSerializer.serialize(cell.clone());
            out.grid[i] = ing;
        }
        if (!any) {
            throw new IllegalArgumentException("Нет ингредиентов в сетке 3×3");
        }
        return out;
    }

    public static ItemStack resolveResult(ItemTemplateService templates, CraftRecipeJson json) {
        if (json.resultStackBase64 != null && !json.resultStackBase64.isBlank()) {
            ItemStack s = ItemStackSerializer.deserialize(json.resultStackBase64).clone();
            int amt = json.resultAmount <= 0 ? Math.max(1, s.getAmount()) : Math.min(64, json.resultAmount);
            s.setAmount(amt);
            return s;
        }
        if (json.resultTemplateId != null && !json.resultTemplateId.isBlank()) {
            ItemStack s = templates.cloneTemplate(json.resultTemplateId)
                .orElseThrow(() -> new IllegalStateException("Неизвестный шаблон результата: " + json.resultTemplateId));
            s.setAmount(Math.max(1, Math.min(64, json.resultAmount)));
            return s;
        }
        throw new IllegalStateException("В рецепте нет ни resultStackBase64, ни resultTemplateId");
    }

    /** Одна ячейка сетки → стак для ExactChoice (null = воздух). */
    public static ItemStack resolveSlot(ItemTemplateService templates, CraftIngredientJson ing) {
        if (ing == null || isEmptySlot(ing)) {
            return null;
        }
        if (ing.templateId != null && !ing.templateId.isBlank()) {
            int amt = ing.amount <= 0 ? 1 : Math.min(64, ing.amount);
            ItemStack s = templates.cloneTemplate(ing.templateId)
                .orElseThrow(() -> new IllegalStateException("Неизвестный шаблон: " + ing.templateId));
            s.setAmount(amt);
            return s;
        }
        if (ing.stackBase64 != null && !ing.stackBase64.isBlank()) {
            ItemStack s = ItemStackSerializer.deserialize(ing.stackBase64).clone();
            if (ing.amount > 0) {
                s.setAmount(Math.min(64, ing.amount));
            }
            return s;
        }
        return null;
    }

    public static ItemStack[] resolveGrid(ItemTemplateService templates, CraftRecipeJson json) {
        normalize(json);
        ItemStack[] out = new ItemStack[9];
        if (json.grid != null && json.grid.length == 9) {
            for (int i = 0; i < 9; i++) {
                out[i] = resolveSlot(templates, json.grid[i]);
            }
            return out;
        }
        List<ItemStack> list = resolveIngredientsLegacy(templates, json);
        for (int i = 0; i < list.size() && i < 9; i++) {
            out[i] = list.get(i).clone();
        }
        return out;
    }

    /** Для совместимости: плоский список непустых ячеек по порядку 0…8. */
    public static List<ItemStack> resolveIngredients(ItemTemplateService templates, CraftRecipeJson json) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : resolveGrid(templates, json)) {
            if (s != null && !s.getType().isAir()) {
                list.add(s);
            }
        }
        return list;
    }

    private static List<ItemStack> resolveIngredientsLegacy(ItemTemplateService templates, CraftRecipeJson json) {
        List<ItemStack> list = new ArrayList<>();
        if (json.ingredients == null) {
            return list;
        }
        for (CraftIngredientJson ing : json.ingredients) {
            ItemStack s = resolveSlot(templates, ing);
            if (s != null && !s.getType().isAir()) {
                list.add(s);
            }
        }
        return list;
    }
}
