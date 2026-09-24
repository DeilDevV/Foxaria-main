package com.foxaria.moderation.gui;

import com.foxaria.api.model.StaffCheckSession;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.moderation.JdbcModerationService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Comparator;
import java.util.function.Consumer;

public final class ActiveChecksMenu extends BaseMenu {

    private final JdbcModerationService moderationService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final Consumer<Player> staffHomeBack;

    public ActiveChecksMenu(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService) {
        this(moderationService, securityService, auditService, null);
    }

    public ActiveChecksMenu(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService, Consumer<Player> staffHomeBack) {
        super("&8⟨ &c&lПроверки &8⟩", 54);
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.staffHomeBack = staffHomeBack;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(4, MenuItems.item(
            org.bukkit.Material.SPYGLASS,
            "&c&lАктивные проверки",
            "&7Сессии на читы и правила сервера",
            "&8───────────────",
            "&eЛКМ &8→ &7карточка игрока",
            "&6ПКМ &8→ &7завершить проверку",
            "&8Shift+ПКМ &7→ тихое завершение"
        ), null);

        int slot = 19;
        for (StaffCheckSession session : moderationService.activeChecks().stream()
            .sorted(Comparator.comparingLong(StaffCheckSession::startedAt))
            .toList()) {
            if (slot >= 45) {
                break;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUuid());
            String targetName = target.getName() == null ? session.targetUuid().toString() : target.getName();
            String duration = formatDuration(System.currentTimeMillis() - session.startedAt());
            setItem(slot++, MenuItems.head(
                target,
                "&f&l" + targetName,
                "&8───────────────",
                "&7Ведёт: &f" + session.actorName(),
                "&7Длительность: &e" + duration,
                "&7Режим вызова: " + (session.silent() ? "&8тихий" : "&aстандарт"),
                "&8───────────────",
                "&eЛКМ &7профиль  &6ПКМ &7закрыть сессию"
            ), click -> {
                if (click.isRightClick()) {
                    moderationService.finishCheck(viewer, session.targetUuid(), click.isShiftClick(), "gui_finish");
                    render(viewer);
                    viewer.openInventory(inventory());
                    return;
                }
                PlayerProfileMenu menu = new PlayerProfileMenu(session.targetUuid(), moderationService, securityService, auditService, null, staffHomeBack);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
        }

        if (slot == 19) {
            setItem(22, MenuItems.item(
                org.bukkit.Material.LIME_STAINED_GLASS_PANE,
                "&a&lВсё спокойно",
                "&7Никто не на проверке — отличный момент",
                "&7для патруля или отдыха."
            ), null);
        }

        setItem(49, MenuItems.item(
            org.bukkit.Material.ARROW,
            "&7← &fНазад",
            staffHomeBack != null ? "&7К панели персонала" : "&7К главной панели модерации"
        ), click -> {
            if (staffHomeBack != null) {
                staffHomeBack.accept(viewer);
            } else {
                ModerationDashboardMenu menu = new ModerationDashboardMenu(moderationService, securityService, auditService);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            }
        });
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).toSeconds();
        if (minutes > 0) {
            return minutes + "м " + seconds + "с";
        }
        return seconds + "с";
    }
}
