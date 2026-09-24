package com.foxaria.shop.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import com.foxaria.shop.ConfigShopService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ShopCategoryMenu extends BaseMenu {

    private final ConfigShopService shopService;

    public ShopCategoryMenu(ConfigShopService shopService) {
        super("&8⟨ &6&lМагазин &8│ &fза монеты &8⟩", 27);
        this.shopService = shopService;
    }

    @Override
    protected void draw(Player player) {
        for (int slot = 0; slot < 27; slot++) {
            int col = slot % 9;
            if (slot < 9 || slot >= 18 || col == 0 || col == 8) {
                setItem(slot, MenuItems.filler(), null);
            }
        }

        setItem(4, MenuItems.item(Material.GOLD_NUGGET, "&6&lМагазин за монеты",
            "&7Покупка и продажа предметов.",
            MenuStyle.divider(),
            "&8Часть товаров открывается",
            "&8с ростом &dуровня знаний &8(&f/quest&8)."), null);

        int slot = 10;
        for (var entry : shopService.categories().entrySet()) {
            if (slot > 16) {
                break;
            }
            String category = entry.getKey();
            int count = shopService.offers(category).size();
            setItem(slot++, MenuItems.item(Material.EMERALD,
                "&a&l" + stripColors(entry.getValue()),
                "&7Товаров в разделе: &f" + count,
                MenuStyle.divider(),
                MenuStyle.hintLeft("открыть раздел")
            ), click -> {
                ShopOfferMenu menu = new ShopOfferMenu(shopService, category);
                menu.render(player);
                player.openInventory(menu.inventory());
            });
        }

        if (slot == 10) {
            setItem(13, MenuItems.item(Material.BARRIER, "&c&lПусто",
                "&7Разделы магазина ещё не настроены."), null);
        }

        setItem(22, MenuStyle.closeButton(), click -> player.closeInventory());
    }

    private String stripColors(String value) {
        return value == null ? "" : value.replaceAll("&[0-9a-fk-or]", "");
    }
}
