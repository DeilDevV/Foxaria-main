package com.foxaria.crafting.listener;

import com.foxaria.core.gui.MenuHolder;
import com.foxaria.crafting.gui.CraftRecipeDetailMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Останавливает таймер анимации печи только когда закрывают именно меню рецепта.
 * <p>
 * Если отменять при любом {@link InventoryCloseEvent}, то при переходе со списка крафта
 * на экран рецепта сначала приходит close предыдущего GUI — и сносится только что
 * запланированный таймер нового окна (картинка замирает на первом кадре).
 */
public final class CraftMenuAnimCleanupListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory closed = event.getInventory();
        if (closed.getHolder() instanceof MenuHolder mh
            && mh.menu() instanceof CraftRecipeDetailMenu) {
            CraftRecipeDetailMenu.cancelAnimOnClose(player);
        }
    }
}
