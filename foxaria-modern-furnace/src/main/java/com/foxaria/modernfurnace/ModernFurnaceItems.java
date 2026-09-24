package com.foxaria.modernfurnace;

import com.foxaria.core.text.FoxariaText;
import com.google.gson.Gson;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class ModernFurnaceItems {

    public static final String TYPE_FURNACE = "furnace";
    public static final String TYPE_FURNACE_MAX = "furnace_max";
    public static final String TYPE_KEY = "key";

    private ModernFurnaceItems() {
    }

    public static ItemStack modernFurnace(ModernFurnaceKeys keys, boolean maxed) {
        ItemStack it = new ItemStack(Material.FURNACE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(FoxariaText.legacy("&6&l✦ Модернизированная печь &8│ &7Foxaria"));
        List<Component> lore = new ArrayList<>();
        lore.add(FoxariaText.legacy("&8 "));
        lore.add(FoxariaText.legacy("&7Печь с &fGUI&7, прокачками и &eтрубами&7."));
        lore.add(FoxariaText.legacy("&7Ставь блок и &fПКМ&7 — меню."));
        if (maxed) {
            lore.add(FoxariaText.legacy("&d&lМакс. улучшения &7(донат/админ)"));
        }
        lore.add(FoxariaText.legacy("&8 "));
        lore.add(FoxariaText.legacy("&8Серверный предмет Foxaria"));
        meta.lore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        String t = maxed ? TYPE_FURNACE_MAX : TYPE_FURNACE;
        meta.getPersistentDataContainer().set(keys.itemType, PersistentDataType.STRING, t);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack key(ModernFurnaceKeys keys) {
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(FoxariaText.legacy("&e&lКлюч"));
        List<Component> lore = new ArrayList<>();
        lore.add(FoxariaText.legacy("&7Подключение &fтруб &7к сундукам"));
        lore.add(FoxariaText.legacy("&7ПКМ по печи — меню труб; затем §fэтим ключом §7ПКМ по сундуку."));
        lore.add(FoxariaText.legacy("&8 "));
        lore.add(FoxariaText.legacy("&8Серверный предмет Foxaria"));
        meta.lore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(keys.itemType, PersistentDataType.STRING, TYPE_KEY);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isModernFurnace(ModernFurnaceKeys keys, ItemStack it) {
        if (it == null || it.getType() != Material.FURNACE || !it.hasItemMeta()) {
            return false;
        }
        String v = it.getItemMeta().getPersistentDataContainer().get(keys.itemType, PersistentDataType.STRING);
        return TYPE_FURNACE.equals(v) || TYPE_FURNACE_MAX.equals(v);
    }

    public static boolean isMaxFurnaceItem(ModernFurnaceKeys keys, ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return false;
        }
        return TYPE_FURNACE_MAX.equals(it.getItemMeta().getPersistentDataContainer().get(keys.itemType, PersistentDataType.STRING));
    }

    public static boolean isKey(ModernFurnaceKeys keys, ItemStack it) {
        if (it == null || it.getType() != Material.TRIPWIRE_HOOK || !it.hasItemMeta()) {
            return false;
        }
        return TYPE_KEY.equals(it.getItemMeta().getPersistentDataContainer().get(keys.itemType, PersistentDataType.STRING));
    }

    public static void attachStateBlob(ModernFurnaceKeys keys, ItemStack drop, String jsonState) {
        ItemMeta meta = drop.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(keys.serializedState, PersistentDataType.STRING, jsonState);
        drop.setItemMeta(meta);
    }

    public static String readStateBlob(ModernFurnaceKeys keys, ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(keys.serializedState, PersistentDataType.STRING);
    }

    public static void clearStateBlob(ModernFurnaceKeys keys, ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(keys.serializedState);
        it.setItemMeta(meta);
    }

    /** Сохраняем полный снимок печи в дроп (при ломании). */
    public static ItemStack dropFromBlock(ModernFurnaceKeys keys, PersistedFurnaceJson state, boolean wasMaxTemplate) {
        ItemStack stack = modernFurnace(keys, wasMaxTemplate);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keys.serializedState, PersistentDataType.STRING, new Gson().toJson(state));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}