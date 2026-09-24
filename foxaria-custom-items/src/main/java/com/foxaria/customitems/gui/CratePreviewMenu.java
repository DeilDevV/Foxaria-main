package com.foxaria.customitems.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.customitems.ConfigCrateService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class CratePreviewMenu extends BaseMenu {

    private final ConfigCrateService.CrateDefinition crate;

    public CratePreviewMenu(ConfigCrateService.CrateDefinition crate) {
        super("&eНаграды кейса: " + crate.id(), 54);
        this.crate = crate;
    }

    @Override
    protected void draw(Player player) {
        int slot = 0;
        for (ConfigCrateService.CrateReward reward : crate.rewards()) {
            ItemStack icon = new ItemStack(iconMaterial(reward.rarity()));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(FoxariaText.legacy(reward.displayName()));
            meta.lore(FoxariaText.legacyLore(
                "&7Редкость: &f" + reward.rarity(),
                "&7Вес: &f" + reward.weight(),
                "&8" + reward.action()
            ));
            icon.setItemMeta(meta);
            setItem(slot++, icon, null);
            if (slot >= inventory().getSize()) {
                break;
            }
        }
    }

    private Material iconMaterial(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "legendary" -> Material.NETHER_STAR;
            case "epic" -> Material.AMETHYST_SHARD;
            case "rare" -> Material.DIAMOND;
            default -> Material.GOLD_NUGGET;
        };
    }
}
