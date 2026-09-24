package com.foxaria.shop.progression;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ProgressionCategory {
    FOOD("food", "&e&lЕда", Material.BREAD),
    RESOURCES("resources", "&6&lРесурсы", Material.IRON_INGOT),
    EQUIPMENT("equipment", "&b&lСнаряжение", Material.DIAMOND_SWORD),
    BLOCKS("blocks", "&7&lБлоки", Material.OAK_LOG),
    SPECIAL("special", "&d&lОсобое", Material.NETHER_STAR);

    private final String id;
    private final String titleLegacy;
    private final Material icon;

    ProgressionCategory(String id, String titleLegacy, Material icon) {
        this.id = id;
        this.titleLegacy = titleLegacy;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public String titleLegacy() {
        return titleLegacy;
    }

    public Material icon() {
        return icon;
    }

    public static Optional<ProgressionCategory> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String k = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.id.equals(k)).findFirst();
    }
}
