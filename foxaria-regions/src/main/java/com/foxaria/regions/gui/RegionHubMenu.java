package com.foxaria.regions.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionInvUtil;
import com.foxaria.regions.RegionItems;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RegionHubMenu extends BaseMenu {

    private final RegionFacade f;

    public RegionHubMenu(RegionFacade f) {
        super("&8Регионы", 54);
        this.f = f;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            if (i != 22 && i != 31) {
                setItem(i, MenuItems.filler(), null);
            }
        }
        int needL = f.config().craftLogsRequired;
        int needI = f.config().craftIronRequired;
        boolean match = f.config().craftWoodMustMatchType;
        int haveL = match ? Math.max(0, maxSameLogs(player, needL)) : RegionInvUtil.totalLogs(player);
        int haveIron = RegionInvUtil.countIron(player);
        setItem(22, MenuItems.item(Material.SMITHING_TABLE, "&e&lПолучить ядро привата",
            "&7Нужно: &f" + needL + " &7брёвен" + (match ? " &7одного типа" : "") + " и &f" + needI + " &7железа.",
            "&7У вас: &f" + haveL + " &7/ &f" + needL + " &7дерева, &f" + haveIron + " &7/ &f" + needI + " &7слитков.",
            "&aЛКМ — забрать ресурсы и выдать блок"), e -> craft(player, match));
        setItem(31, MenuItems.item(Material.ARROW, "&7Закрыть"), e -> player.closeInventory());
    }

    private static int maxSameLogs(Player player, int min) {
        int best = 0;
        for (Material m : Material.values()) {
            if (!Tag.LOGS.isTagged(m)) {
                continue;
            }
            best = Math.max(best, RegionInvUtil.countLogs(player, m));
        }
        return best;
    }

    private void craft(Player player, boolean match) {
        int needL = f.config().craftLogsRequired;
        int needI = f.config().craftIronRequired;
        if (RegionInvUtil.countIron(player) < needI) {
            f.messages().send(player, "region.craft-no-iron", "&cНедостаточно железных слитков.");
            return;
        }
        if (match) {
            Material kind = RegionInvUtil.findLogTypeWithCount(player, needL);
            if (kind == null) {
                f.messages().send(player, "region.craft-no-logs", "&cНужно &f" + needL + " &cбрёвен одного вида.");
                return;
            }
            RegionInvUtil.takeIron(player, needI);
            RegionInvUtil.takeLogsOf(player, kind, needL);
        } else {
            if (RegionInvUtil.totalLogs(player) < needL) {
                f.messages().send(player, "region.craft-no-logs", "&cНедостаточно дерева.");
                return;
            }
            RegionInvUtil.takeIron(player, needI);
            RegionInvUtil.takeLogs(player, needL);
        }
        ItemStack give = RegionItems.privatCabinet(f.plugin());
        player.getInventory().addItem(give);
        f.messages().send(player, "region.craft-done", "&aВы получили ядро привата.");
        player.closeInventory();
    }
}
