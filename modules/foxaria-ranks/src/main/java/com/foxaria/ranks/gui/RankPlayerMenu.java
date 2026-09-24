package com.foxaria.ranks.gui;

import com.foxaria.api.service.RankService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.text.FoxariaText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RankPlayerMenu extends BaseMenu {

    private final RankService rankService;
    private final FileConfiguration config;

    public RankPlayerMenu(RankService rankService, FileConfiguration config) {
        super("&8⟨ &b&lРанги &8⟩", 54);
        this.rankService = rankService;
        this.config = config;
    }

    @Override
    protected void draw(Player viewer) {
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            ItemStack icon = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(FoxariaText.noItalic(net.kyori.adventure.text.Component.text(target.getName())));
            icon.setItemMeta(meta);
            setItem(slot++, icon, click -> {
                RankSelectMenu menu = new RankSelectMenu(rankService, target, rankService instanceof com.foxaria.ranks.StandaloneRankService standalone
                    ? standalone.groups()
                    : config.getStringList("groups"));
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
            if (slot >= inventory().getSize()) {
                break;
            }
        }
    }
}
