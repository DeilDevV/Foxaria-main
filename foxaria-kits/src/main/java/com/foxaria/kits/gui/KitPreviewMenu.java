package com.foxaria.kits.gui;

import com.foxaria.api.model.KitDefinition;
import com.foxaria.core.gui.BaseMenu;
import org.bukkit.entity.Player;

public final class KitPreviewMenu extends BaseMenu {

    private final KitDefinition definition;

    public KitPreviewMenu(KitDefinition definition) {
        super("&eПросмотр: " + definition.displayName(), 54);
        this.definition = definition;
    }

    @Override
    protected void draw(Player player) {
        for (int index = 0; index < 36; index++) {
            var stack = definition.contents().storageSlot(index);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            setItem(index, stack.clone(), null);
        }
    }
}
