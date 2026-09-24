package com.foxaria.donateshop;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum DonateCategory {
    ARMOR("armor", "&b&lБроня", Material.DIAMOND_CHESTPLATE),
    WEAPONS("weapons", "&c&lОружие", Material.NETHERITE_SWORD),
    TOTEMS("totems", "&e&lТотемы", Material.TOTEM_OF_UNDYING),
    RUNES("runes", "&d&lРуны", Material.ENCHANTED_BOOK),
    POTIONS("potions", "&5&lЗелья", Material.POTION),
    OTHER("other", "&7&lОстальное", Material.CHEST);

    private final String id;
    private final String displayLegacy;
    private final Material icon;

    DonateCategory(String id, String displayLegacy, Material icon) {
        this.id = id;
        this.displayLegacy = displayLegacy;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public String displayLegacy() {
        return displayLegacy;
    }

    public Material iconMaterial() {
        return icon;
    }

    public static Optional<DonateCategory> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String k = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(c -> c.id.equals(k))
            .findFirst();
    }
}
