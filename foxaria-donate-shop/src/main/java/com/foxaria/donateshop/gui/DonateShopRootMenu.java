package com.foxaria.donateshop.gui;

import com.foxaria.donateshop.DonateCategory;
import com.foxaria.donateshop.DonateShopService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.text.FoxariaText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DonateShopRootMenu extends BaseMenu {

    private final DonateShopService service;

    public DonateShopRootMenu(DonateShopService service) {
        super("&d&lДонат-магазин &7| &fтокены", 54);
        this.service = service;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
        }

        setItem(4, MenuItems.item(Material.NETHER_STAR, "&d&lДонат-магазин", new String[]{
            "&7Оплата только &bтокенами&7.",
            "&7Токены — на сайте и в акциях сервера.",
            "&8 ",
            "&7Загрузка баланса…",
            "&7Команда: &f/token"
        }), null);

        service.economy().balance(player.getUniqueId()).thenAccept(snap ->
            service.plugin().getServer().getScheduler().runTask(service.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                setItem(4, MenuItems.item(Material.NETHER_STAR, "&d&lДонат-магазин", new String[]{
                    "&7Оплата только &bтокенами&7.",
                    "&7Токены — на сайте и в акциях сервера.",
                    "&8 ",
                    "&7Ваши токены: &b" + snap.tokens(),
                    "&7Проверить: &f/token"
                }), null);
            })
        );

        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        DonateCategory[] cats = DonateCategory.values();
        for (int i = 0; i < cats.length && i < slots.length; i++) {
            DonateCategory cat = cats[i];
            ItemStack icon = new ItemStack(cat.iconMaterial());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(FoxariaText.legacy(cat.displayLegacy()));
            icon.setItemMeta(meta);
            int slot = slots[i];
            setItem(slot, icon, e -> service.openCategory(player, cat));
        }

        setItem(45, MenuItems.item(Material.BARRIER, "&cЗакрыть", "&7Закрыть"), e -> player.closeInventory());
    }
}
