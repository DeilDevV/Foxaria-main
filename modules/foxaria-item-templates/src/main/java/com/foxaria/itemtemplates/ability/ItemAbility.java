package com.foxaria.itemtemplates.ability;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Каталог особых способностей Foxaria.
 *
 * Каждая способность настраивается тремя числами:
 *   chance  — шанс срабатывания, 0..100 %
 *   power   — сила (радиус взрыва, урон, уровень эффекта — зависит от способности)
 *   seconds — длительность, если применимо
 *
 * Слот {@link Slot} определяет, когда способность срабатывает: в ближнем бою,
 * от стрелы, при получении урона или постоянно (пассивно в руке).
 */
public enum ItemAbility {

    // ─── Ближний бой ───
    EXPLOSIVE_STRIKE("explosive_strike", "Взрывной удар", Slot.MELEE, Material.TNT,
        "Взрыв в точке попадания без разрушения блоков", 25, 3, 0,
        "радиус", "—", true, false),

    KNOCKBACK_BLAST("knockback_blast", "Ударная волна", Slot.MELEE, Material.PISTON,
        "Отбрасывает цель и всех рядом стоящих", 30, 3, 0,
        "сила", "—", true, false),

    FROST_BITE("frost_bite", "Обморожение", Slot.ANY_ATTACK, Material.BLUE_ICE,
        "Замедляет и сковывает цель на время", 35, 2, 3,
        "уровень", "секунды", true, true),

    LIFE_STEAL("life_steal", "Вампиризм", Slot.MELEE, Material.REDSTONE,
        "Восстанавливает часть нанесённого урона", 100, 20, 0,
        "% от урона", "—", true, false),

    LIGHTNING_STRIKE("lightning_strike", "Удар молнии", Slot.ANY_ATTACK, Material.LIGHTNING_ROD,
        "Призывает молнию в цель", 15, 1, 0,
        "—", "—", true, false),

    CHAIN_LIGHTNING("chain_lightning", "Цепная молния", Slot.ANY_ATTACK, Material.COPPER_BLOCK,
        "Разряд перескакивает на ближайших врагов", 20, 3, 0,
        "целей", "—", true, false),

    EXECUTE("execute", "Добивание", Slot.MELEE, Material.NETHERITE_AXE,
        "Добивает цель с низким запасом здоровья", 100, 25, 0,
        "% порога HP", "—", true, false),

    WITHER_TOUCH("wither_touch", "Иссушение", Slot.ANY_ATTACK, Material.WITHER_SKELETON_SKULL,
        "Накладывает иссушение на цель", 25, 1, 4,
        "уровень", "секунды", true, true),

    POISON_BLADE("poison_blade", "Ядовитый клинок", Slot.ANY_ATTACK, Material.SPIDER_EYE,
        "Отравляет цель при попадании", 30, 1, 5,
        "уровень", "секунды", true, true),

    BLIND_STRIKE("blind_strike", "Ослепление", Slot.ANY_ATTACK, Material.INK_SAC,
        "Ослепляет цель — полезно против стрелков", 20, 0, 4,
        "—", "секунды", true, true),

    FIRE_TRAIL("fire_trail", "Испепеление", Slot.ANY_ATTACK, Material.BLAZE_POWDER,
        "Поджигает цель на время", 30, 0, 5,
        "—", "секунды", true, true),

    // ─── Лук и стрелы ───
    HOMING_ARROW("homing_arrow", "Самонаведение", Slot.BOW, Material.SPECTRAL_ARROW,
        "Стрела доворачивает к ближайшей цели в полёте", 100, 12, 0,
        "радиус поиска", "—", true, false),

    EXPLOSIVE_ARROW("explosive_arrow", "Разрывная стрела", Slot.BOW, Material.FIRE_CHARGE,
        "Стрела взрывается при попадании", 35, 2, 0,
        "радиус", "—", true, false),

    MULTI_SHOT("multi_shot", "Веерный выстрел", Slot.BOW, Material.ARROW,
        "Выпускает дополнительные стрелы веером", 100, 2, 0,
        "доп. стрел", "—", true, false),

    TELEPORT_ARROW("teleport_arrow", "Стрела-телепорт", Slot.BOW, Material.ENDER_PEARL,
        "Переносит стрелка в точку попадания", 20, 0, 0,
        "—", "—", true, false),

    // ─── Защита ───
    THORNS_AURA("thorns_aura", "Шипы возмездия", Slot.DEFENSE, Material.CACTUS,
        "Возвращает часть урона атакующему", 40, 30, 0,
        "% возврата", "—", true, false),

    SECOND_WIND("second_wind", "Второе дыхание", Slot.DEFENSE, Material.TOTEM_OF_UNDYING,
        "При смертельном уроне даёт регенерацию и щит", 100, 2, 6,
        "уровень", "секунды", true, true),

    DODGE("dodge", "Уклонение", Slot.DEFENSE, Material.FEATHER,
        "Шанс полностью увернуться от удара", 15, 0, 0,
        "—", "—", true, false),

    // ─── Пассивные ───
    SWIFTNESS("swiftness", "Лёгкость", Slot.PASSIVE, Material.SUGAR,
        "Ускорение, пока предмет в руке", 100, 1, 0,
        "уровень", "—", false, false),

    NIGHT_HUNTER("night_hunter", "Ночной охотник", Slot.PASSIVE, Material.GOLDEN_CARROT,
        "Ночное зрение, пока предмет в руке", 100, 0, 0,
        "—", "—", false, false),

    STRENGTH_AURA("strength_aura", "Аура силы", Slot.PASSIVE, Material.BLAZE_ROD,
        "Усиление, пока предмет в руке", 100, 1, 0,
        "уровень", "—", false, false);

    /** Когда срабатывает способность. */
    public enum Slot {
        MELEE("Ближний бой", "&c"),
        BOW("Лук и стрелы", "&a"),
        ANY_ATTACK("Любая атака", "&6"),
        DEFENSE("Защита", "&9"),
        PASSIVE("Пассивно в руке", "&b");

        private final String title;
        private final String color;

        Slot(String title, String color) {
            this.title = title;
            this.color = color;
        }

        public String title() {
            return title;
        }

        public String color() {
            return color;
        }
    }

    private final String id;
    private final String title;
    private final Slot slot;
    private final Material icon;
    private final String description;
    private final int defaultChance;
    private final int defaultPower;
    private final int defaultSeconds;
    private final String powerLabel;
    private final String secondsLabel;
    private final boolean usesChance;
    private final boolean usesSeconds;

    ItemAbility(String id, String title, Slot slot, Material icon, String description,
                int defaultChance, int defaultPower, int defaultSeconds,
                String powerLabel, String secondsLabel, boolean usesChance, boolean usesSeconds) {
        this.id = id;
        this.title = title;
        this.slot = slot;
        this.icon = icon;
        this.description = description;
        this.defaultChance = defaultChance;
        this.defaultPower = defaultPower;
        this.defaultSeconds = defaultSeconds;
        this.powerLabel = powerLabel;
        this.secondsLabel = secondsLabel;
        this.usesChance = usesChance;
        this.usesSeconds = usesSeconds;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Slot slot() {
        return slot;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public int defaultChance() {
        return defaultChance;
    }

    public int defaultPower() {
        return defaultPower;
    }

    public int defaultSeconds() {
        return defaultSeconds;
    }

    public String powerLabel() {
        return powerLabel;
    }

    public String secondsLabel() {
        return secondsLabel;
    }

    public boolean usesChance() {
        return usesChance;
    }

    public boolean usesPower() {
        return !"—".equals(powerLabel);
    }

    public boolean usesSeconds() {
        return usesSeconds;
    }

    public static Optional<ItemAbility> byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (ItemAbility ability : values()) {
            if (ability.id.equals(needle)) {
                return Optional.of(ability);
            }
        }
        return Optional.empty();
    }

    public static List<ItemAbility> bySlot(Slot slot) {
        return java.util.Arrays.stream(values()).filter(a -> a.slot == slot).toList();
    }
}
