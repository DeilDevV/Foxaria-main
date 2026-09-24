package com.foxaria.itemtemplates.edit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/**
 * Правки прочности и бонусов атрибутов (без зачарований). Модификаторы с ключами foxaria:tpl-*.
 */
public final class HandItemAttributeUtil {

    private HandItemAttributeUtil() {
    }

    public static boolean isAirOrEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    public static EquipmentSlotGroup slotGroupFor(ItemStack stack) {
        if (isArmor(stack.getType())) {
            return EquipmentSlotGroup.ARMOR;
        }
        return EquipmentSlotGroup.HAND;
    }

    public static boolean isArmor(Material type) {
        if (type == Material.ELYTRA) {
            return true;
        }
        String n = type.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    public static NamespacedKey tplKey(JavaPlugin plugin, Attribute attribute) {
        String k = "tpl-" + attribute.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return new NamespacedKey(plugin, k);
    }

    public static void removeOurModifier(ItemMeta meta, JavaPlugin plugin, Attribute attribute) {
        NamespacedKey key = tplKey(plugin, attribute);
        Collection<AttributeModifier> mods = meta.getAttributeModifiers(attribute);
        if (mods == null || mods.isEmpty()) {
            return;
        }
        for (AttributeModifier mod : new ArrayList<>(mods)) {
            if (key.equals(mod.getKey())) {
                meta.removeAttributeModifier(attribute, mod);
            }
        }
    }

    public static double ourBonus(ItemMeta meta, JavaPlugin plugin, Attribute attribute) {
        NamespacedKey key = tplKey(plugin, attribute);
        Collection<AttributeModifier> mods = meta.getAttributeModifiers(attribute);
        if (mods == null) {
            return 0D;
        }
        for (AttributeModifier mod : mods) {
            if (key.equals(mod.getKey()) && mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                return mod.getAmount();
            }
        }
        return 0D;
    }

    public static void adjustAttributeBonus(JavaPlugin plugin, ItemStack stack, Attribute attribute, double delta) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        EquipmentSlotGroup group = slotGroupFor(stack);
        double next = ourBonus(meta, plugin, attribute) + delta;
        removeOurModifier(meta, plugin, attribute);
        if (Math.abs(next) > 1e-9) {
            AttributeModifier mod = new AttributeModifier(
                tplKey(plugin, attribute),
                next,
                AttributeModifier.Operation.ADD_NUMBER,
                group
            );
            meta.addAttributeModifier(attribute, mod);
        }
        stack.setItemMeta(meta);
    }

    public static void changeDurability(ItemStack stack, int deltaDamage) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int max = stack.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        int d = damageable.getDamage() + deltaDamage;
        d = Math.max(0, Math.min(max, d));
        damageable.setDamage(d);
        stack.setItemMeta(damageable);
    }

    public static void setDurabilityPercent(ItemStack stack, double fraction) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int max = stack.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        double f = Math.max(0.0, Math.min(1.0, fraction));
        damageable.setDamage((int) Math.round(max * (1.0 - f)));
        stack.setItemMeta(damageable);
    }

    public static void toggleUnbreakable(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setUnbreakable(!meta.isUnbreakable());
        stack.setItemMeta(meta);
    }

    public static void clearFoxariaTemplateModifiers(JavaPlugin plugin, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        String ns = plugin.getName().toLowerCase(Locale.ROOT);
        Multimap<Attribute, AttributeModifier> all = meta.getAttributeModifiers();
        if (all == null || all.isEmpty()) {
            return;
        }
        for (Attribute attribute : new ArrayList<>(all.keySet())) {
            for (AttributeModifier mod : new ArrayList<>(all.get(attribute))) {
                NamespacedKey k = mod.getKey();
                if (ns.equals(k.getNamespace()) && k.getKey().startsWith("tpl-")) {
                    meta.removeAttributeModifier(attribute, mod);
                }
            }
        }
        stack.setItemMeta(meta);
    }
}
