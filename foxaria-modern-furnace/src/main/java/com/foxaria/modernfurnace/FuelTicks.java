package com.foxaria.modernfurnace;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Длительность горения топлива (тики печки), как в ванилле.
 */
public final class FuelTicks {

    private FuelTicks() {
    }

    public static boolean isFuel(ItemStack stack) {
        return burnTicks(stack) > 0;
    }

    public static int burnTicks(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        Material m = stack.getType();
        return switch (m) {
            case LAVA_BUCKET -> 20_000;
            case COAL_BLOCK -> 16_000;
            case DRIED_KELP_BLOCK -> 4_000;
            case BLAZE_ROD -> 2_400;
            case COAL, CHARCOAL -> 1_600;
            case STICK -> 100;
            case BOWL -> 200;
            case BAMBOO, SCAFFOLDING -> 50;
            case AZALEA, FLOWERING_AZALEA -> 100;
            default -> woodLike(m);
        };
    }

    private static int woodLike(Material m) {
        String n = m.name();
        if (n.endsWith("_BOAT") || n.endsWith("_CHEST_BOAT")) {
            return 1_200;
        }
        if (n.endsWith("_SLAB")) {
            return 150;
        }
        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_WOOD") || n.endsWith("_PLANKS")
            || n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE") || n.endsWith("_STAIRS")
            || n.endsWith("_PRESSURE_PLATE") || n.endsWith("_TRAPDOOR") || n.endsWith("_DOOR")
            || n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN")) {
            return 300;
        }
        return 0;
    }
}
