package com.foxaria.admin.gui;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.RankService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Управление донатом с сервера: токены и донат-ранги по онлайну.
 * До этого донат жил только на сайте, а в админ-панели раздела не было вовсе.
 */
public final class DonateAdminMenu extends BaseMenu {

    private final EconomyService economyService;
    private final RankService rankService;
    private final AuditService auditService;
    private final Consumer<Player> back;

    public DonateAdminMenu(EconomyService economyService, RankService rankService, AuditService auditService, Consumer<Player> back) {
        super("&8⟨ &6&lДонат &8⟩", 54);
        this.economyService = economyService;
        this.rankService = rankService;
        this.auditService = auditService;
        this.back = back;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(4, MenuItems.item(
            Material.SUNFLOWER,
            "&6&lДонат &8· &fуправление",
            "&7Токены и донат-ранги игроков онлайн.",
            "&8───────────────",
            "&7Полный набор: &f/fdonate help",
            "&7Выдача с сайта продолжает работать."
        ), null);

        setItem(48, MenuItems.item(
            Material.BOOK,
            "&e&lКоманды доната",
            "&7/fdonate info &8— сводка по игроку",
            "&7/fdonate tokens &8— выдать токены",
            "&7/fdonate take &8— забрать токены",
            "&7/fdonate rank &8— выдать ранг (можно на дни)",
            "&7/fdonate unrank &8— вернуть default"
        ), null);

        if (back != null) {
            setItem(45, MenuItems.item(Material.ARROW, "&7&lНазад", "&7Вернуться в админ-панель"),
                click -> back.accept(viewer));
        }

        setItem(50, MenuItems.item(Material.ENDER_EYE, "&d&lОбновить", "&7Перечитать токены и ранги"),
            click -> {
                render(viewer);
                viewer.openInventory(inventory());
            });

        int slot = 19;
        for (Player target : Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList()) {
            if (slot >= 44) {
                break;
            }
            slot = nextContentSlot(slot);
            final Player card = target;
            setItem(slot++, MenuItems.head(
                target,
                "&f&l" + target.getName(),
                "&8───────────────",
                "&eЛКМ &7→ &b+100 токенов",
                "&6ПКМ &7→ &b-100 токенов",
                "&7Shift+ЛКМ &7→ &fсводка в чат",
                "&8───────────────",
                "&8Ранги: &7/fdonate rank " + target.getName() + " vip"
            ), click -> {
                if (click.isShiftClick()) {
                    info(viewer, card);
                    return;
                }
                if (click.isRightClick()) {
                    economyService.withdrawTokens(card.getUniqueId(), 100L, "admin-menu:" + viewer.getName(), viewer.getUniqueId())
                        .thenRun(() -> notify(viewer, "&e-100 токенов → &f" + card.getName()));
                    audit(viewer, card, "tokens:-100");
                    return;
                }
                economyService.depositTokens(card.getUniqueId(), 100L, "admin-menu:" + viewer.getName(), viewer.getUniqueId())
                    .thenRun(() -> notify(viewer, "&a+100 токенов → &f" + card.getName()));
                audit(viewer, card, "tokens:+100");
            });
        }

        if (slot == 19) {
            setItem(22, MenuItems.item(
                Material.LIME_STAINED_GLASS_PANE,
                "&a&lПусто",
                "&7Сейчас никого нет в сети.",
                "&7Оффлайн-выдача: &f/fdonate tokens <ник> <кол-во>"
            ), null);
        }
    }

    private void info(Player viewer, Player target) {
        economyService.balance(target.getUniqueId()).thenAccept(balance ->
            rankService.primaryGroup(target.getUniqueId()).thenAccept(group -> {
                notify(viewer, "&6Донат-профиль: &f" + target.getName());
                notify(viewer, "&7Ранг: &f" + (group == null ? "default" : group));
                notify(viewer, "&7Токены: &b" + balance.tokens());
            }));
    }

    private void notify(Player viewer, String legacy) {
        if (viewer.isOnline()) {
            viewer.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', legacy));
        }
    }

    private void audit(Player viewer, Player target, String action) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.append(new AuditEvent(
                "donate-admin",
                viewer.getUniqueId(),
                target.getUniqueId(),
                viewer.getName(),
                target.getName(),
                action,
                Map.of("source", "admin-menu"),
                System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
    }

    private int nextContentSlot(int slot) {
        if (slot % 9 == 0) {
            return slot + 1;
        }
        if (slot % 9 == 8) {
            return slot + 2;
        }
        return slot;
    }
}
