package com.foxaria.customitems.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import com.foxaria.customitems.ConfigCrateService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class CratePreviewMenu extends BaseMenu {

    private final ConfigCrateService.CrateDefinition crate;
    private final ConfigCrateService crateService;

    public CratePreviewMenu(ConfigCrateService.CrateDefinition crate) {
        this(crate, null);
    }

    public CratePreviewMenu(ConfigCrateService.CrateDefinition crate, ConfigCrateService crateService) {
        super("&8⟨ &6&lКейс &8│ &f" + crate.id() + " &8⟩", 54);
        this.crate = crate;
        this.crateService = crateService;
    }

    @Override
    protected void draw(Player player) {
        for (int slot = 45; slot < 54; slot++) {
            setItem(slot, MenuStyle.separator(), null);
        }

        int totalWeight = crate.rewards().stream().mapToInt(ConfigCrateService.CrateReward::weight).sum();

        int slot = 0;
        for (ConfigCrateService.CrateReward reward : crate.rewards()) {
            if (slot >= 45) {
                break;
            }
            String chance = totalWeight > 0
                ? String.format(Locale.ROOT, "%.2f%%", reward.weight() * 100.0 / totalWeight)
                : "—";
            setItem(slot++, MenuItems.item(
                iconMaterial(reward.rarity()),
                rarityColor(reward.rarity()) + stripColors(reward.displayName()),
                "&7Редкость: " + rarityColor(reward.rarity()) + reward.rarity(),
                "&7Шанс выпадения: &f" + chance,
                MenuStyle.divider(),
                "&8Награда выдаётся автоматически"
            ), null);
        }

        if (crateService != null) {
            setItem(45, MenuStyle.backButton(), click -> {
                CrateBrowserMenu menu = new CrateBrowserMenu(crateService);
                menu.render(player);
                player.openInventory(menu.inventory());
            });
        }

        setItem(49, MenuItems.item(Material.TRIPWIRE_HOOK, "&6&lО кейсе",
            "&7Наград всего: &f" + crate.rewards().size(),
            "&7Нужен ключ: &f" + crate.keyName(),
            MenuStyle.divider(),
            "&8Шансы рассчитаны от суммы весов."), null);

        setItem(53, MenuStyle.closeButton(), click -> player.closeInventory());
    }

    private String stripColors(String value) {
        return value == null ? "" : value.replaceAll("&[0-9a-fk-or]", "");
    }

    private String rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toLowerCase(Locale.ROOT)) {
            case "legendary" -> "&6&l";
            case "epic" -> "&d";
            case "rare" -> "&b";
            default -> "&f";
        };
    }

    private Material iconMaterial(String rarity) {
        return switch (rarity == null ? "" : rarity.toLowerCase(Locale.ROOT)) {
            case "legendary" -> Material.NETHER_STAR;
            case "epic" -> Material.AMETHYST_SHARD;
            case "rare" -> Material.DIAMOND;
            default -> Material.GOLD_NUGGET;
        };
    }
}
