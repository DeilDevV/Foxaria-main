package com.foxaria.moderation.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Снимок инвентаря игрока «только для чтения».
 *
 * До этого модер-панель открывала живой инвентарь цели — модератор мог
 * вытащить предметы. Модерации достаточно видеть содержимое, поэтому
 * показываем копию: BaseMenu отменяет клики, забрать ничего нельзя.
 */
public final class ReadOnlyInventoryView extends BaseMenu {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final String targetName;

    private ReadOnlyInventoryView(Player target, boolean enderChest) {
        super("&8⟨ &6" + target.getName() + " &8· просмотр ⟩", 54);
        this.targetName = target.getName();
        this.contents = enderChest
            ? target.getEnderChest().getContents().clone()
            : target.getInventory().getStorageContents().clone();
        this.armor = enderChest ? new ItemStack[0] : target.getInventory().getArmorContents().clone();
        this.offhand = enderChest ? null : target.getInventory().getItemInOffHand().clone();
    }

    public static void open(Player viewer, Player target, boolean enderChest) {
        ReadOnlyInventoryView view = new ReadOnlyInventoryView(target, enderChest);
        view.render(viewer);
        viewer.openInventory(view.inventory());
    }

    @Override
    protected void draw(Player viewer) {
        for (int i = 0; i < contents.length && i < 36; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                setItem(i, contents[i].clone(), null);
            }
        }
        for (int i = 0; i < armor.length && i < 4; i++) {
            if (armor[i] != null && !armor[i].getType().isAir()) {
                setItem(45 + i, armor[i].clone(), null);
            }
        }
        if (offhand != null && !offhand.getType().isAir()) {
            setItem(49, offhand.clone(), null);
        }
        setItem(53, MenuItems.item(
            Material.PAPER,
            "&7&lПросмотр: &f" + targetName,
            "&7Это снимок инвентаря.",
            "&7Изменения не сохраняются."
        ), null);
    }
}
