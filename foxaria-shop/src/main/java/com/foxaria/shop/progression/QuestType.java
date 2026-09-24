package com.foxaria.shop.progression;

import java.util.Locale;

public enum QuestType {
    ENTITY_KILL,
    PLAYER_KILL,
    BLOCK_BREAK,
    BLOCK_PLACE,
    CONSUME_ITEM,
    FISH,
    ENCHANT_ITEM,
    CRAFT_ITEM,
    PLAY_MINUTES,
    INTERACT_BLOCK,
    DAMAGE_TAKEN,
    /**
     * Сдать указанное количество предметов (material) из инвентаря частями — через клик по активному квесту в меню.
     */
    SUBMIT_ITEMS,
    UNKNOWN;

    public static QuestType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return QuestType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
