package com.foxaria.regions;

import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class RegionItems {

    private RegionItems() {
    }

    public static ItemStack privatCabinet(JavaPlugin plugin) {
        return privatCabinet(plugin, 1);
    }

    public static ItemStack privatCabinet(JavaPlugin plugin, int level) {
        int lv = Math.max(1, level);
        ItemStack s = new ItemStack(Material.SMITHING_TABLE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(FoxariaText.noItalic(Component.text("Ядро привата", NamedTextColor.YELLOW, TextDecoration.BOLD)));
            List<Component> lore = new ArrayList<>();
            lore.add(FoxariaText.plain("Поставьте, чтобы создать приват (2 блока по высоте).").color(NamedTextColor.GRAY));
            lore.add(FoxariaText.plain("Уровень привата: " + lv).color(NamedTextColor.AQUA));
            lore.add(FoxariaText.plain("После сноса уровень сохраняется на предмете;").color(NamedTextColor.DARK_GRAY));
            lore.add(FoxariaText.plain("список участников при сносе сбрасывается.").color(NamedTextColor.DARK_GRAY));
            m.lore(lore);
            m.getPersistentDataContainer().set(RegionKeys.privatItem(plugin), PersistentDataType.BYTE, (byte) 1);
            m.getPersistentDataContainer().set(RegionKeys.privatLevel(plugin), PersistentDataType.INTEGER, lv);
            s.setItemMeta(m);
        }
        return s;
    }

    public static boolean isPrivat(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte v = stack.getItemMeta().getPersistentDataContainer().get(RegionKeys.privatItem(plugin), PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    public static int privatSavedLevel(JavaPlugin plugin, ItemStack stack) {
        if (!isPrivat(plugin, stack) || !stack.hasItemMeta()) {
            return 1;
        }
        Integer v = stack.getItemMeta().getPersistentDataContainer().get(RegionKeys.privatLevel(plugin), PersistentDataType.INTEGER);
        return v == null || v < 1 ? 1 : v;
    }

    public static ItemStack raidDynamite(JavaPlugin plugin, int tier, int damage) {
        ItemStack s = new ItemStack(Material.TNT);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(FoxariaText.noItalic(Component.text("Динамит рейда " + tier, NamedTextColor.RED, TextDecoration.BOLD)));
            m.lore(List.of(
                FoxariaText.plain("Урон блокам в привате: " + damage).color(NamedTextColor.GRAY),
                FoxariaText.plain("Поджечь или нажать ПКМ по блоку").color(NamedTextColor.DARK_GRAY)
            ));
            m.getPersistentDataContainer().set(RegionKeys.dynamiteTier(plugin), PersistentDataType.INTEGER, tier);
            s.setItemMeta(m);
        }
        return s;
    }

    public static int dynamiteTier(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Integer t = stack.getItemMeta().getPersistentDataContainer().get(RegionKeys.dynamiteTier(plugin), PersistentDataType.INTEGER);
        return t == null ? 0 : t;
    }
}
