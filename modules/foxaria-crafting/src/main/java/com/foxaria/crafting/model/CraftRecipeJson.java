package com.foxaria.crafting.model;

import java.util.ArrayList;
import java.util.List;

public final class CraftRecipeJson {
    /**
     * {@code TABLE} — верстак 3×3. {@code FURNACE} — обычная печь. {@code BLAST_FURNACE} — плавильная печь.
     * Для печей: один вход (первая непустая ячейка сетки) + результат; опционально бонус по шансу.
     */
    public String creationKind = "TABLE";
    /**
     * Если false — рецепт Bukkit не регистрируется (только описание в /craft и/или только бонус обрабатывается кодом).
     */
    public boolean registerPrimaryRecipe = true;
    public int smeltCookTicks = 200;
    public float smeltExperience = 0.2f;
    /** Шаблон предмета для дополнительного дропа при плавке (0 = выключено). */
    public String bonusTemplateId = "";
    /** Шанс бонуса 0–100. */
    public double bonusChancePercent = 0;
    public String displayName = "";
    public int requiredKnowledge = 1;
    public String resultTemplateId = "";
    public String resultStackBase64 = "";
    public int resultAmount = 1;
    /**
     * Фиксированная сетка 3×3 (индексы 0–8, сверху вниз, слева направо). null — пустой слот.
     * Если null, используется устаревший {@link #ingredients} (порядок как раньше, по строкам).
     */
    public CraftIngredientJson[] grid;
    /** Устаревший формат (бесформенный список). */
    public List<CraftIngredientJson> ingredients = new ArrayList<>();
}
