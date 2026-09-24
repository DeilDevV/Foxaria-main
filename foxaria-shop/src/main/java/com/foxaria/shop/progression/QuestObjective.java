package com.foxaria.shop.progression;

import java.util.Locale;

/**
 * Одна цель внутри квеста (в квесте может быть несколько).
 */
public record QuestObjective(
    QuestType type,
    String entity,
    String material,
    int amount,
    String label
) {
    public static QuestObjective fromLegacy(
        QuestType type,
        String entity,
        String material,
        int amount
    ) {
        return new QuestObjective(type, entity == null ? "" : entity, material == null ? "" : material, amount, "");
    }

    /** Строка для GUI: прогресс по этой цели. */
    public String summaryForLore(int current) {
        if (label != null && !label.isBlank()) {
            return label
                .replace("<cur>", String.valueOf(current))
                .replace("<need>", String.valueOf(amount));
        }
        return switch (type) {
            case ENTITY_KILL, PLAYER_KILL ->
                "&7Убить &f" + entity + "&7: &e" + current + "&7/&f" + amount;
            case BLOCK_BREAK ->
                "&7Сломать &f" + material + "&7: &e" + current + "&7/&f" + amount;
            case BLOCK_PLACE ->
                "&7Поставить &f" + material + "&7: &e" + current + "&7/&f" + amount;
            case CONSUME_ITEM ->
                "&7Съесть &f" + material + "&7: &e" + current + "&7/&f" + amount;
            case CRAFT_ITEM ->
                "&7Скрафтить &f" + material + "&7: &e" + current + "&7/&f" + amount;
            case SUBMIT_ITEMS ->
                "&7Сдать &f" + material + "&7: &e" + current + "&7/&f" + amount;
            case FISH -> "&7Рыбалка: &e" + current + "&7/&f" + amount;
            case ENCHANT_ITEM ->
                "&7Зачарования (ур.): &e" + current + "&7/&f" + amount;
            case PLAY_MINUTES -> "&7Минут онлайн: &e" + current + "&7/&f" + amount;
            case INTERACT_BLOCK ->
                "&7Действие с &f" + material + "&7: &e" + current + "&7/&f" + amount;
            case DAMAGE_TAKEN -> "&7Получить урон: &e" + current + "&7/&f" + amount;
            default -> "&7" + type.name().toLowerCase(Locale.ROOT) + ": &e" + current + "&7/&f" + amount;
        };
    }

    public String displayLine() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return switch (type) {
            case ENTITY_KILL, PLAYER_KILL -> "&7Убить: &f" + entity + " &8×&f" + amount;
            case BLOCK_BREAK -> "&7Сломать: &f" + material + " &8×&f" + amount;
            case BLOCK_PLACE -> "&7Поставить: &f" + material + " &8×&f" + amount;
            case CONSUME_ITEM -> "&7Съесть: &f" + material + " &8×&f" + amount;
            case CRAFT_ITEM -> "&7Скрафтить: &f" + material + " &8×&f" + amount;
            case SUBMIT_ITEMS -> "&7Сдать: &f" + material + " &8×&f" + amount;
            case FISH -> "&7Поймать рыбу: &f" + amount + " &7раз";
            case ENCHANT_ITEM -> "&7Зачаровать предметы (&f" + amount + "&7 уровней)";
            case PLAY_MINUTES -> "&7Онлайн: &f" + amount + " &7мин";
            case INTERACT_BLOCK -> "&7Взаимодействие: &f" + material + " &8×&f" + amount;
            case DAMAGE_TAKEN -> "&7Получить урон: &f" + amount;
            default -> "&7" + type.name().toLowerCase(Locale.ROOT);
        };
    }

    public boolean matchesEvent(QuestType eventType, String eventKey) {
        if (type != eventType) {
            return false;
        }
        if (eventKey == null) {
            return false;
        }
        return switch (type) {
            case ENTITY_KILL, PLAYER_KILL -> entity != null && entity.equalsIgnoreCase(eventKey);
            case BLOCK_BREAK, BLOCK_PLACE, CONSUME_ITEM, CRAFT_ITEM, INTERACT_BLOCK ->
                material != null && material.equalsIgnoreCase(eventKey);
            case FISH, ENCHANT_ITEM, DAMAGE_TAKEN, PLAY_MINUTES -> true;
            case SUBMIT_ITEMS -> false;
            default -> false;
        };
    }
}
