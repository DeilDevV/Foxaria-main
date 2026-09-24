package com.foxaria.ranks.gui;

import com.foxaria.api.service.RankService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.ranks.StandaloneRankService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class RankSelectMenu extends BaseMenu {

    private final RankService rankService;
    private final Player target;
    private final List<String> groups;

    public RankSelectMenu(RankService rankService, Player target, List<String> groups) {
        super("&8⟨ &b&lРанги &8│ &f" + target.getName() + " &8⟩", 27);
        this.rankService = rankService;
        this.target = target;
        this.groups = groups;
    }

    @Override
    protected void draw(Player viewer) {
        int slot = 0;
        for (String group : groups) {
            ItemStack icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            String displayName = rankService instanceof StandaloneRankService standaloneRankService
                ? standaloneRankService.displayName(group)
                : group;
            meta.displayName(FoxariaText.noItalic(net.kyori.adventure.text.Component.text(displayName)));
            icon.setItemMeta(meta);
            setItem(slot++, icon, click -> rankService.setPrimaryGroup(target.getUniqueId(), group));
            if (slot >= inventory().getSize()) {
                break;
            }
        }
    }
}
