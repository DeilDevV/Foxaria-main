package com.foxaria.customitems.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import com.foxaria.customitems.ConfigCrateService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CrateBrowserMenu extends BaseMenu {

    private final ConfigCrateService crateService;

    public CrateBrowserMenu(ConfigCrateService crateService) {
        super("&8⟨ &6&lКейсы &8⟩", 27);
        this.crateService = crateService;
    }

    @Override
    protected void draw(Player player) {
        for (int slot = 0; slot < 27; slot++) {
            int col = slot % 9;
            if (slot < 9 || slot >= 18 || col == 0 || col == 8) {
                setItem(slot, MenuItems.filler(), null);
            }
        }

        setItem(4, MenuItems.item(Material.TRIPWIRE_HOOK, "&6&lКейсы Foxaria",
            "&7Выбери кейс, чтобы посмотреть",
            "&7полный список наград и шансы.",
            MenuStyle.divider(),
            "&8Ключи выпадают за активность",
            "&8и продаются на сайте."), null);

        int slot = 10;
        for (ConfigCrateService.CrateDefinition crate : crateService.crates()) {
            if (slot > 16) {
                break;
            }
            setItem(slot++, MenuItems.item(Material.CHEST,
                "&6&l" + stripColors(crate.displayName()),
                "&7Наград в кейсе: &f" + crate.rewards().size(),
                "&7Ключ: &f" + crate.keyName(),
                MenuStyle.divider(),
                MenuStyle.hintLeft("посмотреть награды")
            ), click -> {
                CratePreviewMenu menu = new CratePreviewMenu(crate, crateService);
                menu.render(player);
                player.openInventory(menu.inventory());
            });
        }

        if (slot == 10) {
            setItem(13, MenuItems.item(Material.BARRIER, "&c&lКейсов нет",
                "&7Кейсы ещё не настроены на сервере."), null);
        }

        setItem(22, MenuStyle.closeButton(), click -> player.closeInventory());
    }

    private String stripColors(String value) {
        return value == null ? "" : value.replaceAll("&[0-9a-fk-or]", "");
    }
}
