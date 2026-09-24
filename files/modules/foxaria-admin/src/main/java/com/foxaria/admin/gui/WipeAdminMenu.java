package com.foxaria.admin.gui;

import com.foxaria.admin.wipe.WipeService;
import com.foxaria.admin.wipe.WipeTarget;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Раздел ВАЙП — опасные операции.
 *
 * Специально оформлен иначе, чем остальные панели: красная рамка и явные
 * предупреждения, чтобы админ не нажал сюда «на автомате». Каждая кнопка
 * требует второго клика для подтверждения и имеет кулдаун.
 */
public final class WipeAdminMenu extends BaseMenu {

    private static final int[] TARGET_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private final JavaPlugin plugin;
    private final WipeService wipe;
    private final Consumer<Player> back;

    public WipeAdminMenu(JavaPlugin plugin, WipeService wipe, Consumer<Player> back) {
        super("&8⟨ &4&lВАЙП &8│ &cосторожно &8⟩", 54);
        this.plugin = plugin;
        this.wipe = wipe;
        this.back = back;
    }

    @Override
    protected void draw(Player viewer) {
        drawDangerFrame();

        setItem(4, MenuItems.item(
            Material.TNT,
            "&4&l⚠ ОПАСНАЯ ЗОНА ⚠",
            "&cЗдесь безвозвратно удаляются игровые данные.",
            MenuStyle.divider(),
            "&7Не затрагивается никогда:",
            "&a• донат-токены и купленные ранги",
            "&a• баны, муты и история наказаний",
            "&a• аккаунты и пароли игроков",
            MenuStyle.divider(),
            "&7Каждая кнопка: &fпервый клик — запрос,",
            "&fвторой в течение " + wipe.confirmWindowSeconds() + " сек — запуск&7.",
            "&8Постройки вайпаются заменой карты, не отсюда."
        ), null);

        for (int i = 0; i < WipeTarget.values().length && i < TARGET_SLOTS.length; i++) {
            WipeTarget target = WipeTarget.values()[i];
            setItem(TARGET_SLOTS[i], targetIcon(viewer, target), click -> handleClick(viewer, target));
        }

        setItem(40, MenuItems.item(
            Material.WRITABLE_BOOK,
            "&e&lЖурнал очисток",
            buildJournal()
        ), null);

        if (back != null) {
            setItem(45, MenuStyle.backButton(), click -> {
                wipe.cancelConfirm(viewer.getUniqueId());
                back.accept(viewer);
            });
        }

        setItem(49, MenuItems.item(
            Material.LIME_DYE,
            "&a&lОтменить подтверждение",
            "&7Сбрасывает ожидание второго клика.",
            "&8Нажми, если передумал."
        ), click -> {
            wipe.cancelConfirm(viewer.getUniqueId());
            refresh(viewer);
            viewer.sendMessage("§aПодтверждение сброшено.");
        });

        setItem(53, MenuStyle.closeButton(), click -> {
            wipe.cancelConfirm(viewer.getUniqueId());
            viewer.closeInventory();
        });
    }

    private org.bukkit.inventory.ItemStack targetIcon(Player viewer, WipeTarget target) {
        boolean awaiting = wipe.awaitingConfirm(viewer.getUniqueId(), target);
        long cooldown = wipe.cooldownLeft(target);
        boolean running = wipe.isRunning(target);

        List<String> lore = new ArrayList<>();
        lore.add("&7" + target.description());
        lore.add(MenuStyle.divider());
        lore.add("&7Последняя очистка:");
        lore.add(wipe.lastRunLabel(target));
        lore.add(MenuStyle.divider());

        if (running) {
            lore.add("&e⌛ Выполняется…");
        } else if (cooldown > 0) {
            lore.add("&8Кулдаун: &7" + wipe.formatDuration(cooldown));
        } else if (awaiting) {
            lore.add("&c&lНАЖМИ ЕЩЁ РАЗ ДЛЯ ПОДТВЕРЖДЕНИЯ");
            lore.add("&8Отмена — кнопка снизу или закрыть меню.");
        } else {
            lore.add("&cЛКМ &8→ &7запросить очистку");
        }

        Material icon = awaiting ? Material.RED_CONCRETE : target.icon();
        String title = (awaiting ? "&c&l⚠ " : "&f&l") + target.title();
        return MenuItems.item(icon, title, lore.toArray(new String[0]));
    }

    private void handleClick(Player viewer, WipeTarget target) {
        if (wipe.isRunning(target)) {
            viewer.sendMessage("§eОчистка «" + target.title() + "» уже выполняется.");
            return;
        }
        long cooldown = wipe.cooldownLeft(target);
        if (cooldown > 0) {
            viewer.sendMessage("§cПодождите " + wipe.formatDuration(cooldown)
                + " перед повторной очисткой «" + target.title() + "».");
            return;
        }
        if (!wipe.confirmOrRequest(viewer.getUniqueId(), target)) {
            viewer.sendMessage("§c⚠ Подтвердите: нажмите «" + target.title()
                + "» ещё раз в течение " + wipe.confirmWindowSeconds() + " сек.");
            refresh(viewer);
            return;
        }

        viewer.sendMessage("§eОчистка «" + target.title() + "» запущена…");
        wipe.execute(target, viewer.getUniqueId(), viewer.getName()).whenComplete((rows, error) ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    plugin.getLogger().warning("[wipe] " + target.name() + " failed: " + error.getMessage());
                    if (viewer.isOnline()) {
                        viewer.sendMessage("§cОшибка очистки: " + error.getMessage());
                    }
                    return;
                }
                // Операция такого масштаба должна быть видна в логах всегда,
                // даже если админ уже вышел из игры.
                plugin.getLogger().warning("[wipe] " + target.name() + " выполнен админом "
                    + viewer.getName() + ", затронуто записей: " + rows);
                if (viewer.isOnline()) {
                    viewer.sendMessage("§aГотово: «" + target.title() + "» — записей затронуто: §f" + rows);
                    refresh(viewer);
                }
            }));
    }

    private void refresh(Player viewer) {
        render(viewer);
        viewer.openInventory(inventory());
    }

    private String[] buildJournal() {
        List<String> lines = new ArrayList<>();
        lines.add("&7Что и когда очищалось:");
        lines.add(MenuStyle.divider());
        for (WipeTarget target : WipeTarget.values()) {
            lines.add("&f" + target.title() + "&8: " + wipe.lastRunLabel(target));
        }
        lines.add(MenuStyle.divider());
        lines.add("&8В скобках — число затронутых записей.");
        return lines.toArray(new String[0]);
    }

    /** Красная рамка: раздел визуально отличается от обычных панелей. */
    private void drawDangerFrame() {
        int size = inventory().getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == 8;
            if (!border) {
                continue;
            }
            boolean corner = (row == 0 || row == rows - 1) && (col == 0 || col == 8);
            setItem(slot, MenuItems.item(
                corner ? Material.RED_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE,
                corner ? "&c✦" : "&6"), null);
        }
    }
}
