package com.foxaria.playershops.gui;

import com.foxaria.api.model.PlayerShop;
import com.foxaria.api.model.PlayerShopOffer;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.playershops.JdbcPlayerShopService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerShopMenu extends BaseMenu {

    private final JdbcPlayerShopService service;
    private final PlayerShop shop;
    private final boolean inspectMode;

    public PlayerShopMenu(JdbcPlayerShopService service, PlayerShop shop, boolean inspectMode) {
        super("&5Магазин: " + shop.name(), 54);
        this.service = service;
        this.shop = shop;
        this.inspectMode = inspectMode;
    }

    @Override
    protected void draw(Player viewer) {
        java.util.concurrent.CompletableFuture<java.util.List<PlayerShopOffer>> future = inspectMode
            ? service.inspectOffers(shop.id())
            : service.offers(shop.id());
        future.thenAccept(offers ->
            viewer.getServer().getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                int slot = 0;
                for (PlayerShopOffer offer : offers) {
                    ItemStack icon = offer.item().clone();
                    ItemMeta meta = icon.getItemMeta();
                    meta.displayName(net.kyori.adventure.text.Component.text("Цена: " + offer.price() + " | Остаток: " + offer.stock()));
                    icon.setItemMeta(meta);
                    setItem(slot++, icon, click -> {
                        if (!inspectMode) {
                            service.buy(viewer, offer.id());
                        }
                    });
                    if (slot >= inventory().getSize()) {
                        break;
                    }
                }
            })
        );
    }
}
