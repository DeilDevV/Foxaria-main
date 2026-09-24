package com.foxaria.shop.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.shop.ConfigShopService;
import com.foxaria.shop.ShopOffer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ShopOfferMenu extends BaseMenu {

    private final ConfigShopService shopService;
    private final String category;

    public ShopOfferMenu(ConfigShopService shopService, String category) {
        super("&8⟨ &6&lМагазин &8│ &f" + category + " &8⟩", 54);
        this.shopService = shopService;
        this.category = category;
    }

    @Override
    protected void draw(Player player) {
        for (int slot = 45; slot < 54; slot++) {
            setItem(slot, MenuStyle.separator(), null);
        }

        int slot = 0;
        for (ShopOffer offer : shopService.offers(category)) {
            if (slot >= 45) {
                break;
            }
            ItemStack icon = shopService.offerIcon(offer).clone();
            ItemMeta meta = icon.getItemMeta();
            // Раньше цена впихивалась в название одной строкой — теперь
            // название отдельно, цены и подсказки в описании.
            meta.displayName(FoxariaText.legacy("&f&l" + stripColors(offer.displayName())));
            List<String> lore = new ArrayList<>();
            lore.add("&7Количество: &f" + offer.amount());
            lore.add(MenuStyle.divider());
            boolean canBuy = offer.buyPrice() != null && offer.buyPrice().doubleValue() > 0;
            boolean canSell = offer.sellPrice() != null && offer.sellPrice().doubleValue() > 0;
            lore.add(canBuy ? "&7Покупка: &a" + offer.buyPrice() : "&8Покупка недоступна");
            lore.add(canSell ? "&7Продажа: &e" + offer.sellPrice() : "&8Продажа недоступна");
            lore.add(MenuStyle.divider());
            if (canBuy) {
                lore.add(MenuStyle.hintLeft("купить 1 шт."));
            }
            if (canSell) {
                lore.add(MenuStyle.hintRight("продать из инвентаря"));
            }
            meta.lore(FoxariaText.legacyLore(lore));
            icon.setItemMeta(meta);
            setItem(slot++, icon, click -> {
                if (click.isRightClick() && canSell) {
                    shopService.sell(player, new ItemStack(offer.material(), offer.amount()), offer.sellPrice());
                    return;
                }
                if (canBuy) {
                    shopService.buy(player, offer.id(), 1);
                }
            });
        }

        setItem(45, MenuStyle.backButton(), click -> {
            ShopCategoryMenu menu = new ShopCategoryMenu(shopService);
            menu.render(player);
            player.openInventory(menu.inventory());
        });

        setItem(49, MenuItems.item(org.bukkit.Material.GOLD_NUGGET, "&6&lРаздел: &f" + category,
            "&7Товаров: &f" + shopService.offers(category).size(),
            MenuStyle.divider(),
            "&8ЛКМ — купить, ПКМ — продать"), null);

        setItem(53, MenuStyle.closeButton(), click -> player.closeInventory());
    }

    private String stripColors(String value) {
        return value == null ? "" : value.replaceAll("&[0-9a-fk-or]", "");
    }
}
