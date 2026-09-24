package com.foxaria.kits.gui;

import com.foxaria.api.model.KitDefinition;
import com.foxaria.api.service.KitService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class KitBrowserMenu extends BaseMenu {

    private static final int PAGE_SIZE = 36;
    private static final int SEPARATOR_START = 36;

    private final KitService kitService;
    private final MenuManager menuManager;
    private final Player viewer;
    private final int page;

    public KitBrowserMenu(KitService kitService, MenuManager menuManager, Player viewer) {
        this(kitService, menuManager, viewer, 0);
    }

    public KitBrowserMenu(KitService kitService, MenuManager menuManager, Player viewer, int page) {
        super("&6&lНаборы", 54);
        this.kitService = kitService;
        this.menuManager = menuManager;
        this.viewer = viewer;
        this.page = Math.max(0, page);
    }

    @Override
    protected void draw(Player player) {
        List<KitDefinition> all = kitService.definitions();
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = SEPARATOR_START; i < SEPARATOR_START + 9; i++) {
            setItem(i, MenuItems.item(Material.BLACK_STAINED_GLASS_PANE, "&8 "), null);
        }

        if (all.isEmpty()) {
            setItem(22, MenuItems.item(Material.BARRIER, "&cНет наборов", "&7Админ может создать: &f/kitadmin create <id>"), null);
            setItem(49, MenuItems.item(Material.BARRIER, "&cЗакрыть", "&7Закрыть"), e -> viewer.closeInventory());
            return;
        }

        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.min(page, totalPages - 1);
        int from = safePage * PAGE_SIZE;
        List<KitDefinition> slice = all.subList(from, Math.min(from + PAGE_SIZE, all.size()));

        int slot = 0;
        for (KitDefinition def : slice) {
            if (slot >= PAGE_SIZE) {
                break;
            }
            setItem(slot++, kitIcon(def), event -> menuManager.open(viewer, new KitDetailMenu(kitService, menuManager, viewer, def)));
        }

        int bottom = 45;
        if (safePage > 0) {
            setItem(bottom, MenuItems.item(Material.ARROW, "&e&lНазад", "&7Страница &f" + safePage + "&7/&f" + totalPages),
                e -> menuManager.open(viewer, new KitBrowserMenu(kitService, menuManager, viewer, safePage - 1)));
        } else {
            setItem(bottom, MenuItems.item(Material.GRAY_DYE, "&8Первая страница", "&7Это начало списка"), null);
        }

        setItem(bottom + 1, MenuItems.item(Material.HOPPER, "&f&lВсе наборы", List.of(
            "&7ЛКМ по набору — просмотр и получение.",
            "&7Всего: &f" + all.size()
        ).toArray(new String[0])), null);

        setItem(bottom + 2, MenuItems.item(Material.EMERALD, "&a&lОбновить", "&7Перечитать список"),
            e -> menuManager.open(viewer, new KitBrowserMenu(kitService, menuManager, viewer, safePage)));

        if (safePage < totalPages - 1) {
            setItem(bottom + 3, MenuItems.item(Material.ARROW, "&e&lДалее", "&7Страница &f" + (safePage + 2) + "&7/&f" + totalPages),
                e -> menuManager.open(viewer, new KitBrowserMenu(kitService, menuManager, viewer, safePage + 1)));
        } else {
            setItem(bottom + 3, MenuItems.item(Material.GRAY_DYE, "&8Последняя страница", "&7Дальше пусто"), null);
        }

        setItem(bottom + 8, MenuItems.item(Material.BARRIER, "&c&lЗакрыть", "&7Закрыть меню"), e -> viewer.closeInventory());
    }

    private ItemStack kitIcon(KitDefinition def) {
        ItemStack icon = def.contents().firstIcon();
        if (icon == null) {
            icon = new ItemStack(Material.CHEST);
        } else {
            icon = icon.clone();
        }
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(FoxariaText.noItalic(Component.text(def.displayName())));
        List<Component> lore = new ArrayList<>();
        lore.add(FoxariaText.legacy("&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪"));
        lore.add(FoxariaText.legacy("&7Предметов: &f" + def.contents().nonEmptyCount()));
        if (def.cooldownSeconds() > 0) {
            lore.add(FoxariaText.legacy("&7Перезарядка: &e" + formatCooldown(def.cooldownSeconds())));
        } else {
            lore.add(FoxariaText.legacy("&7Перезарядка: &aнет"));
        }
        lore.add(FoxariaText.legacy("&8 "));
        lore.add(FoxariaText.legacy("&e▶ ЛКМ — открыть"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String formatCooldown(long seconds) {
        long h = TimeUnit.SECONDS.toHours(seconds);
        long m = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        if (h > 0) {
            return h + " ч " + m + " мин";
        }
        if (m > 0) {
            return m + " мин";
        }
        return seconds + " сек";
    }
}
