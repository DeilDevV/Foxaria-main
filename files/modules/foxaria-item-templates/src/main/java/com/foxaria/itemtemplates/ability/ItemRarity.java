package com.foxaria.itemtemplates.ability;

import java.util.Locale;

/**
 * Редкость предмета — подсветка названия и рамка описания,
 * чтобы особые предметы сразу отличались от ванильных.
 */
public enum ItemRarity {

    COMMON("common", "Обычный", "&7", "&8"),
    UNCOMMON("uncommon", "Необычный", "&a", "&2"),
    RARE("rare", "Редкий", "&b", "&3"),
    EPIC("epic", "Эпический", "&d", "&5"),
    LEGENDARY("legendary", "Легендарный", "&6", "&e"),
    MYTHIC("mythic", "Мифический", "&c", "&4");

    private final String id;
    private final String title;
    private final String color;
    private final String accent;

    ItemRarity(String id, String title, String color, String accent) {
        this.id = id;
        this.title = title;
        this.color = color;
        this.accent = accent;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** Основной цвет названия предмета. */
    public String color() {
        return color;
    }

    /** Цвет рамки и акцентов в описании. */
    public String accent() {
        return accent;
    }

    public String frame() {
        return accent + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    }

    /** Заголовок редкости в описании. */
    public String badge() {
        return color + "✦ " + title.toUpperCase(Locale.ROOT) + " ✦";
    }

    public ItemRarity next() {
        ItemRarity[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public ItemRarity previous() {
        ItemRarity[] all = values();
        return all[(ordinal() - 1 + all.length) % all.length];
    }

    public static ItemRarity byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMMON;
        }
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (ItemRarity rarity : values()) {
            if (rarity.id.equals(needle)) {
                return rarity;
            }
        }
        return COMMON;
    }

    /** Подбирает редкость по числу и силе способностей — для быстрой авто-простановки. */
    public static ItemRarity suggest(int abilityCount) {
        if (abilityCount <= 0) {
            return COMMON;
        }
        if (abilityCount == 1) {
            return UNCOMMON;
        }
        if (abilityCount == 2) {
            return RARE;
        }
        if (abilityCount == 3) {
            return EPIC;
        }
        if (abilityCount == 4) {
            return LEGENDARY;
        }
        return MYTHIC;
    }
}
