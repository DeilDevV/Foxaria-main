package com.foxaria.shop.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.shop.ConfigShopService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ShopCategoryMenu extends BaseMenu {

    private final ConfigShopService shopService;

    public ShopCategoryMenu(ConfigShopService shopService) {
        super("&6&lМагазин за монеты", 27);
        this.shopService = shopService;
    }

    @Override
    protected void draw(Player player) {
        int slot = 10;
        for (var entry : shopService.categories().entrySet()) {
            ItemStack icon = new ItemStack(Material.EMERALD);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(FoxariaText.noItalic(net.kyori.adventure.text.Component.text(entry.getValue())));
            icon.setItemMeta(meta);
            String category = entry.getKey();
            setItem(slot++, icon, click -> {
                ShopOfferMenu menu = new ShopOfferMenu(shopService, category);
                menu.render(player);
                player.openInventory(menu.inventory());
            });
        }
    }
}
