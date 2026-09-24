package com.foxaria.shop.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.shop.ConfigShopService;
import com.foxaria.shop.ShopOffer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ShopOfferMenu extends BaseMenu {

    private final ConfigShopService shopService;
    private final String category;

    public ShopOfferMenu(ConfigShopService shopService, String category) {
        super("&aКатегория: " + category, 54);
        this.shopService = shopService;
        this.category = category;
    }

    @Override
    protected void draw(Player player) {
        int slot = 0;
        for (ShopOffer offer : shopService.offers(category)) {
            ItemStack icon = shopService.offerIcon(offer).clone();
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(FoxariaText.noItalic(net.kyori.adventure.text.Component.text(offer.displayName() + " | покупка " + offer.buyPrice() + " | продажа " + offer.sellPrice())));
            icon.setItemMeta(meta);
            setItem(slot++, icon, click -> {
                if (click.isRightClick() && offer.sellPrice().doubleValue() > 0) {
                    shopService.sell(player, new ItemStack(offer.material(), offer.amount()), offer.sellPrice());
                    return;
                }
                shopService.buy(player, offer.id(), 1);
            });
        }
    }
}
