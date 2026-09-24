package com.foxaria.playershops.gui;

import com.foxaria.api.model.PlayerShop;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.playershops.JdbcPlayerShopService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerShopBrowserMenu extends BaseMenu {

    private final JdbcPlayerShopService service;
    private final boolean includeInactive;

    public PlayerShopBrowserMenu(JdbcPlayerShopService service, boolean includeInactive) {
        super("&8⟨ &d&lМагазины игроков &8⟩", 54);
        this.service = service;
        this.includeInactive = includeInactive;
    }

    @Override
    protected void draw(Player viewer) {
        service.listShops().thenAccept(shops ->
            viewer.getServer().getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                int slot = 0;
                for (PlayerShop shop : shops) {
                    ItemStack icon = new ItemStack(Material.BARREL);
                    ItemMeta meta = icon.getItemMeta();
                    meta.displayName(net.kyori.adventure.text.Component.text(shop.name()));
                    icon.setItemMeta(meta);
                    setItem(slot++, icon, click -> {
                        PlayerShopMenu menu = new PlayerShopMenu(service, shop, includeInactive);
                        menu.render(viewer);
                        viewer.openInventory(menu.inventory());
                    });
                    if (slot >= inventory().getSize()) {
                        break;
                    }
                }
            })
        );
    }
}
