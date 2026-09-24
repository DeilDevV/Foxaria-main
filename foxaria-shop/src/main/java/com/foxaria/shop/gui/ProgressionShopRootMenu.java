package com.foxaria.shop.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.shop.ProgressionShopService;
import com.foxaria.shop.progression.ProgressionCategory;
import com.foxaria.shop.progression.ProgressionService;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ProgressionShopRootMenu extends BaseMenu {

    private final ProgressionShopService shop;
    private final ProgressionService progression;
    private final FileConfiguration cfg;

    public ProgressionShopRootMenu(ProgressionShopService shop, ProgressionService progression, FileConfiguration cfg) {
        super(cfg.getString("gui.root-title", "&6&lМагазин знаний"), 54);
        this.shop = shop;
        this.progression = progression;
        this.cfg = cfg;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, MenuItems.item(Material.BROWN_STAINED_GLASS_PANE, "&8 "), null);
        }

        progression.knowledgeLevel(player.getUniqueId()).thenAccept(kl ->
            shop.plugin().getServer().getScheduler().runTask(shop.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                List<String> rootLore = new ArrayList<>(List.of(
                    "&7Часть товаров закрыта до нужного",
                    "&7уровня &dзнаний&7.",
                    "&8 ",
                    "&7Ваш уровень знаний: &d" + kl,
                    "&7Повысить: &f/quest",
                    "&8 "
                ));
                rootLore.addAll(cfg.getStringList("gui.root-lore-extra"));
                setItem(4, MenuItems.item(Material.EXPERIENCE_BOTTLE, "&6&lМагазин за монеты",
                    rootLore.toArray(new String[0])), null);
            })
        );

        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        ProgressionCategory[] cats = ProgressionCategory.values();
        for (int i = 0; i < cats.length && i < slots.length; i++) {
            ProgressionCategory cat = cats[i];
            ItemStack icon = new ItemStack(cat.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(FoxariaText.legacy(cat.titleLegacy()));
            icon.setItemMeta(meta);
            int slot = slots[i];
            setItem(slot, icon, e -> shop.openCategory(player, cat));
        }

        setItem(45, MenuItems.item(Material.BARRIER, "&cЗакрыть", "&7Закрыть"), e -> player.closeInventory());
    }
}
