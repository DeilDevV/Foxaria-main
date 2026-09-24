package com.foxaria.itemtemplates.edit;

import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Зачарования на предмете в руке (в т.ч. выше лимита — ignoreRestrictions).
 */
public final class HandEnchantUtil {

    private HandEnchantUtil() {
    }

    public static List<Enchantment> allSorted() {
        List<Enchantment> list = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            list.add(e);
        }
        list.sort(Comparator.comparing(e -> e.getKey().asString()));
        return list;
    }

    public static int level(ItemStack stack, Enchantment ench) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        return meta.getEnchantLevel(ench);
    }

    public static void addLevels(ItemStack stack, Enchantment ench, int delta) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        int cur = meta.getEnchantLevel(ench);
        int next = Math.max(0, cur + delta);
        if (next <= 0) {
            meta.removeEnchant(ench);
        } else {
            meta.addEnchant(ench, next, true);
        }
        stack.setItemMeta(meta);
    }

    public static void setMaxLevel(ItemStack stack, Enchantment ench) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.addEnchant(ench, ench.getMaxLevel(), true);
        stack.setItemMeta(meta);
    }

    public static void remove(ItemStack stack, Enchantment ench) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.removeEnchant(ench);
        stack.setItemMeta(meta);
    }
}
