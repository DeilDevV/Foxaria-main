package com.foxaria.admin.gui;

import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ModerationService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.gui.ActiveChecksMenu;
import com.foxaria.moderation.gui.OnlineProfilesMenu;
import com.foxaria.moderation.gui.ReportQueueMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.function.Consumer;

public final class AdminDashboardMenu extends BaseMenu {

    private final EconomyService economyService;
    private final ModerationService moderationService;
    private final SecurityService securityService;
    private final RankService rankService;
    private final AuditService auditService;

    public AdminDashboardMenu(EconomyService economyService, ModerationService moderationService, SecurityService securityService, RankService rankService, AuditService auditService) {
        super("&8⟨ &4&lАдмин &8⟩", 54);
        this.economyService = economyService;
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.rankService = rankService;
        this.auditService = auditService;
    }

    private Consumer<Player> adminHomeBack() {
        return v -> {
            AdminDashboardMenu menu = new AdminDashboardMenu(economyService, moderationService, securityService, rankService, auditService);
            menu.render(v);
            v.openInventory(menu.inventory());
        };
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(4, MenuItems.item(
            org.bukkit.Material.NETHER_STAR,
            "&4&lFOXARIA &8· &fадминистрирование",
            "&7Экономика, ранги, инвентари и санкции",
            "&7в одной сетке — аккуратно и быстро.",
            "&8───────────────",
            "&7ПКМ по санкциям: &8тихий режим"
        ), null);

        setItem(10, MenuItems.item(
            org.bukkit.Material.WRITABLE_BOOK,
            "&6&lЖалобы",
            "&7Очередь репортов от игроков",
            "&eЛКМ &8→ &7открыть список"
        ), click -> openReportQueue(viewer));

        setItem(12, MenuItems.item(
            org.bukkit.Material.SPYGLASS,
            "&c&lПроверки",
            "&7Активных сессий: &f" + moderationService.activeChecks().size(),
            "&7Синхронизировано с модерацией",
            "&eЛКМ &8→ &7список на проверке"
        ), click -> openActiveChecks(viewer));

        setItem(14, MenuItems.item(
            org.bukkit.Material.COMPASS,
            "&b&lОнлайн",
            "&7Все игроки в сети — быстрый доступ к профилям",
            "&eЛКМ &8→ &7полный список"
        ), click -> openOnlineProfiles(viewer));

        setItem(16, MenuItems.item(
            org.bukkit.Material.BOOK,
            "&e&lСправка",
            "&7Жалобы, проверки и карточки ниже —",
            "&7единая логика с мод-панелью."
        ), null);

        int slot = 19;
        for (Player target : Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList()) {
            if (slot >= 45) {
                break;
            }
            slot = nextContentSlot(slot);
            setItem(slot++, MenuItems.head(
                target,
                "&f&l" + target.getName(),
                "&8───────────────",
                "&7Проверка: " + status(moderationService.isUnderCheck(target.getUniqueId())),
                "&7Заморозка: " + status(moderationService.isFrozen(target.getUniqueId())),
                "&7Мут: " + status(moderationService.isMuted(target.getUniqueId())),
                "&8───────────────",
                "&eЛКМ &7админ-профиль"
            ), click -> {
                PlayerAdminMenu menu = new PlayerAdminMenu(target, economyService, moderationService, securityService, rankService, auditService);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
        }

        if (slot == 19) {
            setItem(22, MenuItems.item(
                org.bukkit.Material.LIME_STAINED_GLASS_PANE,
                "&a&lПусто",
                "&7Дождитесь игроков — карточки",
                "&7появятся автоматически."
            ), null);
        }

        setItem(49, MenuItems.item(
            org.bukkit.Material.ENDER_EYE,
            "&d&lОбновить",
            "&7Обновить список онлайна"
        ), click -> {
            render(viewer);
            viewer.openInventory(inventory());
        });
    }

    private void openReportQueue(Player viewer) {
        if (!(moderationService instanceof JdbcModerationService jdbc)) {
            viewer.sendMessage(org.bukkit.ChatColor.RED + "Жалобы доступны только при JDBC-модерации на этом узле.");
            return;
        }
        ReportQueueMenu menu = new ReportQueueMenu(jdbc, securityService, auditService, adminHomeBack());
        menu.render(viewer);
        viewer.openInventory(menu.inventory());
    }

    private void openActiveChecks(Player viewer) {
        if (!(moderationService instanceof JdbcModerationService jdbc)) {
            viewer.sendMessage(org.bukkit.ChatColor.RED + "Проверки доступны только при JDBC-модерации на этом узле.");
            return;
        }
        ActiveChecksMenu menu = new ActiveChecksMenu(jdbc, securityService, auditService, adminHomeBack());
        menu.render(viewer);
        viewer.openInventory(menu.inventory());
    }

    private void openOnlineProfiles(Player viewer) {
        if (!(moderationService instanceof JdbcModerationService jdbc)) {
            viewer.sendMessage(org.bukkit.ChatColor.RED + "Список профилей доступен только при JDBC-модерации на этом узле.");
            return;
        }
        OnlineProfilesMenu menu = new OnlineProfilesMenu(jdbc, securityService, auditService, adminHomeBack());
        menu.render(viewer);
        viewer.openInventory(menu.inventory());
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

    private String status(boolean enabled) {
        return enabled ? "&c● да" : "&7○ нет";
    }
}
