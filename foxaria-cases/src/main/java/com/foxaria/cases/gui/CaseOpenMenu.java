package com.foxaria.cases.gui;

import com.foxaria.cases.model.CaseDefinition;
import com.foxaria.cases.model.CaseLocation;
import com.foxaria.cases.model.CaseReward;
import com.foxaria.cases.service.CaseService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class CaseOpenMenu extends BaseMenu {

    private final CaseService caseService;
    private final Player viewer;
    private final CaseLocation location;
    private final CaseDefinition definition;
    private final int keyCount;

    public CaseOpenMenu(
        CaseService caseService,
        Player viewer,
        CaseLocation location,
        CaseDefinition definition,
        int keyCount
    ) {
        super(definition.menu().title(), 54);
        this.caseService = caseService;
        this.viewer = viewer;
        this.location = location;
        this.definition = definition;
        this.keyCount = keyCount;
    }

    @Override
    protected void draw(Player player) {
        for (int slot = 0; slot < 54; slot++) {
            setItem(slot, MenuItems.filler(), null);
        }
        for (int slot = 36; slot < 45; slot++) {
            setItem(slot, MenuItems.item(Material.GRAY_STAINED_GLASS_PANE, "&8 "), null);
        }

        int rewardSlot = 0;
        for (CaseReward reward : definition.rewards()) {
            if (rewardSlot >= 36) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Редкость: &f" + reward.rarity());
            lore.add("&7Вес: &f" + reward.weight());
            lore.add("&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪");
            lore.addAll(reward.menuLore());
            setItem(rewardSlot++, caseService.items().rewardIcon(reward), null);
        }

        int openSlot = definition.menu().openButtonSlot();
        if (keyCount > 0) {
            setItem(openSlot, MenuItems.item(Material.EMERALD, "&a&lОткрыть кейс", List.of(
                "&7Нажмите, чтобы открыть",
                "&7Ключей: &f" + keyCount
            ).toArray(new String[0])), event -> caseService.beginOpen(viewer, location, definition));
        } else {
            setItem(openSlot, MenuItems.item(Material.BARRIER, "&c&lНет ключей", List.of(
                "&7Купите или получите ключи",
                "&7и вернитесь снова"
            ).toArray(new String[0])), null);
        }

        int keysSlot = definition.menu().keysHintSlot();
        List<String> keysLore = new ArrayList<>();
        keysLore.add("&7Ключей: &f" + keyCount);
        keysLore.add("&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪");
        keysLore.addAll(definition.menu().buyKeysLore());
        setItem(keysSlot, MenuItems.item(Material.TRIPWIRE_HOOK, "&e&lКлючи", keysLore.toArray(new String[0])), null);

        setItem(49, MenuItems.item(Material.BARRIER, "&c&lЗакрыть", "&7Закрыть меню"), event -> viewer.closeInventory());
    }
}
