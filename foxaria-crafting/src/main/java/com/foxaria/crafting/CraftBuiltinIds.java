package com.foxaria.crafting;

/**
 * Виртуальные id для подсказок в /craft без строки в БД.
 */
public final class CraftBuiltinIds {

    /** Встроенная сера (шаблон {@code sulfur} + логика {@link com.foxaria.crafting.smelt.SmeltBonusCoordinator}). */
    public static final String SULFUR = "__foxaria_builtin_sulfur__";

    private CraftBuiltinIds() {
    }
}
