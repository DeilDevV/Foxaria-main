package com.foxaria.kits.gui;

import com.foxaria.api.model.KitDefinition;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class KitPreviewMenu extends BaseMenu {

    private final KitDefinition definition;

    public KitPreviewMenu(KitDefinition definition) {
        super("&8⟨ &6&lНабор &8│ &fпросмотр &8⟩", 54);
        this.definition = definition;
    }

    @Override
    protected void draw(Player player) {
        int items = 0;
        for (int index = 0; index < 36; index++) {
            var stack = definition.contents().storageSlot(index);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            setItem(index, stack.clone(), null);
            items++;
        }

        for (int slot = 36; slot < 45; slot++) {
            setItem(slot, MenuStyle.separator(), null);
        }

        setItem(49, MenuItems.item(Material.CHEST, "&6&l" + definition.displayName(),
            "&7Предметов в наборе: &f" + items,
            MenuStyle.divider(),
            "&8Это предварительный просмотр —",
            "&8забрать отсюда ничего нельзя."), null);

        setItem(53, MenuStyle.closeButton(), click -> player.closeInventory());
    }
}
