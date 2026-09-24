package com.foxaria.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class BaseMenu {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected BaseMenu(String title, int size) {
        this.inventory = Bukkit.createInventory(new MenuHolder(this), size, ChatColor.translateAlternateColorCodes('&', title));
    }

    public final Inventory inventory() {
        return inventory;
    }

    public final void render(Player player) {
        inventory.clear();
        actions.clear();
        draw(player);
    }

    /**
     * Вызывается на следующем тике после {@link MenuManager#open}, когда инвентарь уже открыт у клиента.
     */
    protected void onOpened(Player player) {
    }

    protected abstract void draw(Player player);

    protected final void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    void click(InventoryClickEvent event) {
        event.setCancelled(true);
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    /**
     * Рамка для панелей персонала: тёмное стекло и фиолетовые углы.
     */
    protected final void fillStaffDecorFrame() {
        int size = inventory.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == 8;
            if (!border) {
                continue;
            }
            boolean corner = (row == 0 || row == rows - 1) && (col == 0 || col == 8);
            if (corner) {
                setItem(slot, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&d✦", "&8Foxaria Staff"), null);
            } else {
                setItem(slot, MenuItems.item(Material.BLACK_STAINED_GLASS_PANE, "&8"), null);
            }
        }
    }
}
