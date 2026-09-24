package com.foxaria.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuManager implements Listener {

    private final JavaPlugin plugin;

    public MenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, BaseMenu menu) {
        menu.render(player);
        player.openInventory(menu.inventory());
        plugin.getServer().getScheduler().runTask(plugin, () -> menu.onOpened(player));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MenuHolder menuHolder)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        int raw = event.getRawSlot();

        if (raw < top.getSize()) {
            event.setCancelled(true);
            if (clicked == top) {
                menuHolder.menu().click(event);
            }
            return;
        }

        if (clicked == event.getView().getBottomInventory()) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
