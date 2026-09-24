package com.foxaria.moderation.gui;

import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import com.foxaria.moderation.JdbcModerationService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Comparator;

public final class ModerationDashboardMenu extends BaseMenu {

    private final JdbcModerationService moderationService;
    private final SecurityService securityService;
    private final AuditService auditService;

    public ModerationDashboardMenu(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService) {
        super("&8⟨ &5&lМодерация &8⟩", 54);
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.auditService = auditService;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(4, MenuItems.item(
            org.bukkit.Material.NETHER_STAR,
            "&5&lFOXARIA &8· &fцентр модерации",
            "&7Жалобы, проверки на читы и быстрые действия",
            "&7с игроками — в одном месте.",
            "&8───────────────",
            "&7ЛКМ по кнопкам наказаний: &fвидно персоналу",
            "&7ПКМ: &8тихий режим &7(без лишних сообщений)"
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
            "&7Сейчас активно: &f" + moderationService.activeChecks().size(),
            "&7Вызов, завершение и контроль сессии",
            "&eЛКМ &8→ &7список на проверке"
        ), click -> openActiveChecks(viewer));

        setItem(14, MenuItems.item(
            org.bukkit.Material.COMPASS,
            "&b&lОнлайн",
            "&7Все игроки в сети, сортировка по статусу",
            "&eЛКМ &8→ &7полный список"
        ), click -> openOnlineProfiles(viewer));

        setItem(16, MenuItems.item(
            org.bukkit.Material.BOOK,
            "&e&lСправка",
            "&7Проверку можно снять через GUI или &f/uncheck",
            "&7Заморозка блокирует движение до снятия.",
            "&8Подсказка: &7приоритет у игроков на проверке"
        ), null);

        int slot = 19;
        for (Player target : Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator
                .comparing((Player target) -> !moderationService.isUnderCheck(target.getUniqueId()))
                .thenComparing(target -> !moderationService.isFrozen(target.getUniqueId()))
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList()) {
            if (slot >= 45) {
                break;
            }
            slot = nextContentSlot(slot);
            setItem(slot++, playerCard(target), click -> openPlayerProfile(viewer, target.getUniqueId()));
        }

        if (slot == 19) {
            setItem(22, MenuItems.item(
                org.bukkit.Material.LIME_STAINED_GLASS_PANE,
                "&a&lТихо",
                "&7На сервере пока никого — карточки появятся",
                "&7автоматически, когда зайдут игроки."
            ), null);
        }

        setItem(49, MenuItems.item(
            org.bukkit.Material.ENDER_EYE,
            "&d&lОбновить",
            "&7Синхронизировать список с сервером"
        ), click -> {
            render(viewer);
            viewer.openInventory(inventory());
        });

        setItem(53, MenuStyle.closeButton(), click -> viewer.closeInventory());
    }

    private void openReportQueue(Player viewer) {
        ReportQueueMenu menu = new ReportQueueMenu(moderationService, securityService, auditService);
        menu.render(viewer);
        viewer.openInventory(menu.inventory());
    }

    private void openActiveChecks(Player viewer) {
        ActiveChecksMenu menu = new ActiveChecksMenu(moderationService, securityService, auditService);
        menu.render(viewer);
        viewer.openInventory(menu.inventory());
    }

    private void openOnlineProfiles(Player viewer) {
        OnlineProfilesMenu menu = new OnlineProfilesMenu(moderationService, securityService, auditService);
        menu.render(viewer);
        viewer.openInventory(menu.inventory());
    }

    private void openPlayerProfile(Player viewer, java.util.UUID targetUuid) {
        PlayerProfileMenu menu = new PlayerProfileMenu(targetUuid, moderationService, securityService, auditService, null);
        menu.render(viewer);
        viewer.openInventory(menu.inventory());
    }

    private org.bukkit.inventory.ItemStack playerCard(Player target) {
        OfflinePlayer offlineTarget = target;
        return MenuItems.head(
            offlineTarget,
            "&f&l" + target.getName(),
            "&8───────────────",
            "&7Проверка: " + state(moderationService.isUnderCheck(target.getUniqueId()), "&c● активна", "&a○ нет"),
            "&7Заморозка: " + state(moderationService.isFrozen(target.getUniqueId()), "&b● да", "&7○ нет"),
            "&7Мут: " + state(moderationService.isMuted(target.getUniqueId()), "&e● да", "&7○ нет"),
            "&8───────────────",
            "&eЛКМ &7открыть профиль и действия"
        );
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

    private String state(boolean enabled, String whenTrue, String whenFalse) {
        return enabled ? whenTrue : whenFalse;
    }
}
