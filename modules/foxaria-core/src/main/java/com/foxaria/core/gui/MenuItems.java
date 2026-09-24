package com.foxaria.core.gui;

import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

public final class MenuItems {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private MenuItems() {
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        apply(meta, name, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack head(OfflinePlayer player, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (player != null) {
            meta.setOwningPlayer(player);
        }
        apply(meta, name, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Non-blocking player head: uses PlayerProfile with UUID+name only.
     * Safe to call on the main thread — no Mojang API network request.
     */
    public static ItemStack headByProfile(java.util.UUID uuid, String playerName, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (uuid != null) {
            org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(uuid, playerName != null ? playerName : "");
            meta.setOwnerProfile(profile);
        }
        apply(meta, name, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, "&8");
    }

    private static void apply(ItemMeta meta, String name, String... lore) {
        meta.displayName(FoxariaText.legacy(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (lore.length > 0) {
            List<Component> renderedLore = Arrays.stream(lore)
                .map(FoxariaText::legacy)
                .toList();
            meta.lore(renderedLore);
        }
    }
}
