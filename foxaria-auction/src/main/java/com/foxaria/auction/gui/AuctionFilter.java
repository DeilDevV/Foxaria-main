package com.foxaria.auction.gui;

import org.bukkit.Material;
import org.bukkit.Tag;

public enum AuctionFilter {

    ALL("&f&lВсе лоты"),
    BLOCKS("&7&lБлоки"),
    TOOLS("&e&lИнструменты"),
    COMBAT("&c&lБоевое"),
    ARMOR("&b&lБроня"),
    FOOD("&a&lЕда"),
    POTIONS("&d&lЗелья"),
    OTHER("&8&lПрочее");

    private final String display;

    AuctionFilter(String display) {
        this.display = display;
    }

    String display() {
        return display;
    }

    AuctionFilter next() {
        AuctionFilter[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    boolean matches(Material m) {
        if (m == null || m.isAir()) {
            return false;
        }
        if (this == ALL) {
            return true;
        }
        if (this == OTHER) {
            for (AuctionFilter f : values()) {
                if (f != ALL && f != OTHER && f.matchesDirect(m)) {
                    return false;
                }
            }
            return true;
        }
        return matchesDirect(m);
    }

    private boolean matchesDirect(Material m) {
        return switch (this) {
            case BLOCKS -> m.isBlock();
            case TOOLS -> Tag.ITEMS_PICKAXES.isTagged(m) || Tag.ITEMS_AXES.isTagged(m) || Tag.ITEMS_SHOVELS.isTagged(m)
                || Tag.ITEMS_HOES.isTagged(m) || m == Material.SHEARS || m == Material.FISHING_ROD
                || m == Material.FLINT_AND_STEEL || m == Material.BRUSH;
            case COMBAT -> Tag.ITEMS_SWORDS.isTagged(m) || m == Material.BOW || m == Material.CROSSBOW
                || m == Material.TRIDENT || m == Material.MACE || m == Material.WIND_CHARGE;
            case ARMOR -> armorMaterial(m);
            case FOOD -> m.isEdible();
            case POTIONS -> m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION;
            default -> false;
        };
    }

    private static boolean armorMaterial(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
            || m == Material.ELYTRA || m == Material.TURTLE_HELMET;
    }
}
