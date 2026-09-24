package com.foxaria.customitems.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.customitems.ConfigCrateService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CrateBrowserMenu extends BaseMenu {

    private final ConfigCrateService crateService;

    public CrateBrowserMenu(ConfigCrateService crateService) {
        super("&6Кейсы Foxaria", 27);
        this.crateService = crateService;
    }

    @Override
    protected void draw(Player player) {
        int slot = 10;
        for (ConfigCrateService.CrateDefinition crate : crateService.crates()) {
            ItemStack icon = new ItemStack(Material.TRIPWIRE_HOOK);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(FoxariaText.legacy(crate.displayName()));
            icon.setItemMeta(meta);
            setItem(slot++, icon, click -> {
                CratePreviewMenu menu = new CratePreviewMenu(crate);
                menu.render(player);
                player.openInventory(menu.inventory());
            });
            if (slot >= inventory().getSize()) {
                break;
            }
        }
    }
}
