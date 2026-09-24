package com.foxaria.kits.gui;

import com.foxaria.api.model.KitDefinition;
import com.foxaria.api.service.KitService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class KitDetailMenu extends BaseMenu {

    private static final int ROW_STORAGE_END = 35;
    private static final int ROW_SEP_START = 36;

    private final KitService kitService;
    private final MenuManager menuManager;
    private final Player viewer;
    private final KitDefinition kit;

    public KitDetailMenu(KitService kitService, MenuManager menuManager, Player viewer, KitDefinition kit) {
        super("&8⟨ &6&lНабор &8│ &f" + kit.displayName() + " &8⟩", 54);
        this.kitService = kitService;
        this.menuManager = menuManager;
        this.viewer = viewer;
        this.kit = kit;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = ROW_SEP_START; i < ROW_SEP_START + 9; i++) {
            setItem(i, MenuItems.item(Material.GRAY_STAINED_GLASS_PANE, "&8 "), null);
        }

        for (int s = 0; s <= ROW_STORAGE_END; s++) {
            ItemStack stack = storageSlot(s);
            if (stack != null) {
                setItem(s, decorateInfo(stack, "&7Слот хранения"), null);
            }
        }

        setItem(45, MenuItems.item(Material.ARROW, "&e&lНазад", "&7К списку наборов"),
            e -> menuManager.open(viewer, new KitBrowserMenu(kitService, menuManager, viewer)));

        ItemStack boots = kit.contents().armorSlot(0);
        ItemStack legs = kit.contents().armorSlot(1);
        ItemStack chest = kit.contents().armorSlot(2);
        ItemStack helm = kit.contents().armorSlot(3);
        setArmorSlot(46, boots, "&7Ботинки");
        setArmorSlot(47, legs, "&7Поножи");
        setArmorSlot(48, chest, "&7Нагрудник");
        setArmorSlot(49, helm, "&7Шлем");

        ItemStack off = kit.contents().offhand();
        if (off != null && !off.getType().isAir()) {
            setItem(50, decorateInfo(off.clone(), "&7Левая рука"), null);
        } else {
            setItem(50, MenuItems.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&8Левая рука", "&7Пусто"), null);
        }

        long cd = kit.cooldownSeconds();
        String cdLine = cd <= 0 ? "&aБез перезарядки" : "&eПерезарядка: &f" + formatCooldown(cd);
        setItem(51, MenuItems.item(Material.LIME_CONCRETE, "&a&lПолучить набор", List.of(
            "&7Выдаёт все предметы в инвентарь.",
            cdLine,
            "&8 ",
            "&a▶ Клик — забрать"
        ).toArray(new String[0])),
            e -> {
                kitService.claim(viewer, kit.id());
                viewer.closeInventory();
            });

        setItem(52, MenuItems.item(Material.BOOK, "&f&lИнформация", List.of(
            "&7ID: &f" + kit.id(),
            "&7Предметов: &f" + kit.contents().nonEmptyCount(),
            cdLine,
            "&7Право: &8" + kit.permission()
        ).toArray(new String[0])), null);

        setItem(53, MenuItems.item(Material.BARRIER, "&c&lЗакрыть", "&7Закрыть окно"), e -> viewer.closeInventory());
    }

    private ItemStack storageSlot(int guiSlot) {
        return kit.contents().storageSlot(guiSlot);
    }

    private void setArmorSlot(int slot, ItemStack piece, String roleLine) {
        if (piece != null && !piece.getType().isAir()) {
            setItem(slot, decorateInfo(piece.clone(), roleLine), null);
        } else {
            setItem(slot, MenuItems.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&8—", roleLine + ": &7пусто"), null);
        }
    }

    private static ItemStack decorateInfo(ItemStack stack, String extraLoreLine) {
        var meta = stack.getItemMeta();
        if (meta != null) {
            List<net.kyori.adventure.text.Component> lore = meta.lore() != null
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();
            lore.add(FoxariaText.legacy("&8▪▪▪▪▪▪▪▪▪▪"));
            lore.add(FoxariaText.legacy(extraLoreLine));
            lore.add(FoxariaText.legacy("&7Просмотр — предмет как в наборе."));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String formatCooldown(long seconds) {
        long h = TimeUnit.SECONDS.toHours(seconds);
        long m = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + " ч " + m + " мин";
        }
        if (m > 0) {
            return m + " мин " + s + " сек";
        }
        return s + " сек";
    }
}
