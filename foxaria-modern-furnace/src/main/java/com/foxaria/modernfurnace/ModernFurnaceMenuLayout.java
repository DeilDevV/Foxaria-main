package com.foxaria.modernfurnace;

/**
 * Сетка 54 слота.
 * Ряд 1: входы 10–13, топливо 14.
 * Ряд 2: прогресс по каналам 19–22, по бокам рамка 18 и 26.
 * Ряд 3: выходы 28–31 (готовый ресурс), индикатор тепла 32.
 * Ряд 4: прокачка 40, трубы 42.
 * Низ: закрытие 49.
 */
public final class ModernFurnaceMenuLayout {

    public static final int[] INPUT_SLOTS = {10, 11, 12, 13};
    public static final int FUEL_SLOT = 14;
    /** Готовый ресурс по каналам (ниже полосы прогресса). */
    public static final int[] OUTPUT_SLOTS = {28, 29, 30, 31};
    /** Прогресс плавки по каналам (над выходом). */
    public static final int[] LANE_PROGRESS_SLOTS = {19, 20, 21, 22};
    /** Индикатор тепла / топлива (декор). */
    public static final int FUEL_HEAT_SLOT = 32;
    public static final int UPGRADE_BUTTON = 40;
    public static final int PIPE_BUTTON = 42;
    public static final int CLOSE_SLOT = 49;

    private ModernFurnaceMenuLayout() {
    }

    public static boolean isFuelSlot(int raw) {
        return raw == FUEL_SLOT;
    }

    public static boolean isInputSlot(int raw, int lanes) {
        for (int i = 0; i < lanes && i < INPUT_SLOTS.length; i++) {
            if (raw == INPUT_SLOTS[i]) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOutputSlot(int raw, int lanes) {
        for (int i = 0; i < lanes && i < OUTPUT_SLOTS.length; i++) {
            if (raw == OUTPUT_SLOTS[i]) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSmeltingSlot(int raw, int lanes) {
        return isFuelSlot(raw) || isInputSlot(raw, lanes) || isOutputSlot(raw, lanes);
    }

    /** Декор прогресса и тепла — не забирать предметы. */
    public static boolean isDecorOrButton(int raw) {
        if (raw == UPGRADE_BUTTON || raw == PIPE_BUTTON || raw == CLOSE_SLOT) {
            return true;
        }
        if (raw == FUEL_HEAT_SLOT) {
            return true;
        }
        for (int b : LANE_PROGRESS_SLOTS) {
            if (raw == b) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLockedLaneSlot(int raw, int lanes) {
        for (int i = lanes; i < INPUT_SLOTS.length; i++) {
            if (raw == INPUT_SLOTS[i]) {
                return true;
            }
        }
        for (int i = lanes; i < OUTPUT_SLOTS.length; i++) {
            if (raw == OUTPUT_SLOTS[i]) {
                return true;
            }
        }
        return false;
    }
}
