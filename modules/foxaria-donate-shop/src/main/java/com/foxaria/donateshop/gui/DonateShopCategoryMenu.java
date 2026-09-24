package com.foxaria.donateshop.gui;

import com.foxaria.donateshop.DonateCategory;
import com.foxaria.donateshop.DonateOffer;
import com.foxaria.donateshop.DonateShopService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.text.FoxariaText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DonateShopCategoryMenu extends BaseMenu {

    private static final int AREA = 36;

    private final DonateShopService service;
    private final DonateCategory category;

    public DonateShopCategoryMenu(DonateShopService service, DonateCategory category) {
        super(category.displayLegacy() + " &8| &7токены", 54);
        this.service = service;
        this.category = category;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, MenuItems.item(Material.GRAY_STAINED_GLASS_PANE, "&8 "), null);
        }

        setItem(40, MenuItems.item(Material.BOOK, "&f&lРаздел", new String[]{
            "&7Товары ниже стоят в &bтокенах&7.",
            "&7ЛКМ — купить одну единицу."
        }), null);

        service.offers(category).thenAccept(list ->
            service.plugin().getServer().getScheduler().runTask(service.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                int slot = 0;
                for (DonateOffer offer : list) {
                    if (slot >= AREA) {
                        break;
                    }
                    setItem(slot++, offerIcon(offer), e -> service.purchase(player, offer));
                }
            })
        );

        setItem(45, MenuItems.item(Material.ARROW, "&e&lВсе разделы", "&7Назад"),
            e -> service.openRoot(player));
        setItem(49, MenuItems.item(Material.EMERALD, "&a&lОбновить", "&7Перечитать товары"),
            e -> service.openCategory(player, category));
        setItem(53, MenuItems.item(Material.BARRIER, "&cЗакрыть", "&7Закрыть"), e -> player.closeInventory());
    }

    private ItemStack offerIcon(DonateOffer offer) {
        ItemStack icon = offer.displayItem().clone();
        ItemMeta meta = icon.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() != null
            ? new ArrayList<>(meta.lore())
            : new ArrayList<>();
        lore.add(FoxariaText.legacy("&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪"));
        lore.add(FoxariaText.legacy("&7Цена: &b&l" + offer.priceTokens() + " &7ток."));
        lore.add(FoxariaText.legacy("&a▶ ЛКМ — купить"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }
}
