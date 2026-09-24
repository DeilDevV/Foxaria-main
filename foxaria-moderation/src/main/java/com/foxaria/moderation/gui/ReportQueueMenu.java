package com.foxaria.moderation.gui;

import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.ModerationRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class ReportQueueMenu extends BaseMenu {

    private final JdbcModerationService moderationService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final Consumer<Player> staffHomeBack;

    public ReportQueueMenu(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService) {
        this(moderationService, securityService, auditService, null);
    }

    public ReportQueueMenu(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService, Consumer<Player> staffHomeBack) {
        super("&8⟨ &6&lЖалобы &8⟩", 54);
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.staffHomeBack = staffHomeBack;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(4, MenuItems.item(
            Material.WRITABLE_BOOK,
            "&6&lОчередь жалоб",
            "&7Каждая запись — переход к профилю цели",
            "&7и кнопке закрытия репорта.",
            "&eЛКМ по бумаге &8→ &7профиль + жалоба"
        ), null);

        setItem(22, MenuItems.item(
            Material.GRAY_STAINED_GLASS_PANE,
            "&7Загрузка…",
            "&7Подождите, запрашиваем базу."
        ), null);

        setItem(49, MenuItems.item(
            Material.ARROW,
            "&7← &fНазад",
            staffHomeBack != null ? "&7К админ-панели или панели модерации" : "&7К главной панели модерации"
        ), click -> {
            if (staffHomeBack != null) {
                staffHomeBack.accept(viewer);
            } else {
                ModerationDashboardMenu menu = new ModerationDashboardMenu(moderationService, securityService, auditService);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            }
        });

        JavaPlugin plugin = moderationService.plugin();
        CompletableFuture.supplyAsync(() -> {
            try {
                return moderationService.openReports().join();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Open reports failed", e);
                return null;
            }
        }).whenComplete((reports, throwable) -> moderationService.runSync(() -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (throwable != null || reports == null) {
                setItem(22, MenuItems.item(
                    Material.RED_STAINED_GLASS_PANE,
                    "&cОшибка загрузки",
                    "&7Не удалось прочитать таблицу жалоб.",
                    "&7Проверьте консоль сервера."
                ), null);
                viewer.sendMessage(Component.text("Не удалось загрузить очередь жалоб. См. лог сервера."));
                return;
            }
            fillReports(viewer, reports);
        }));
    }

    private void fillReports(Player viewer, List<ModerationRepository.ReportEntry> reports) {
        if (!reports.isEmpty()) {
            setItem(22, null, null);
        }
        int slot = 19;
        for (ModerationRepository.ReportEntry report : reports) {
            if (slot >= 45) {
                break;
            }
            slot = nextContentSlot(slot);
            OfflinePlayer target = Bukkit.getOfflinePlayer(report.targetUuid());
            OfflinePlayer reporter = Bukkit.getOfflinePlayer(report.reporterUuid());
            String targetName = target.getName() == null ? report.targetUuid().toString() : target.getName();
            String reporterName = reporter.getName() == null ? report.reporterUuid().toString() : reporter.getName();
            String age = formatAge(report.createdAt());
            setItem(slot++, MenuItems.item(
                Material.PAPER,
                "&f&l" + targetName,
                "&8───────────────",
                "&7Жалобу подал: &f" + reporterName,
                "&7Текст: &7" + report.reason(),
                "&7В очереди: &e" + age,
                "&8───────────────",
                "&eЛКМ &7открыть профиль с жалобой"
            ), click -> {
                PlayerProfileMenu menu = new PlayerProfileMenu(report.targetUuid(), moderationService, securityService, auditService, report, staffHomeBack);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
        }

        if (reports.isEmpty()) {
            setItem(22, MenuItems.item(
                Material.LIME_STAINED_GLASS_PANE,
                "&a&lЧистая очередь",
                "&7Нет открытых жалоб — можно выдохнуть",
                "&7или проверить онлайн в соседнем меню."
            ), null);
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

    private String formatAge(long createdAt) {
        Duration duration = Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - createdAt));
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "меньше минуты назад";
        }
        if (minutes < 60) {
            return minutes + " мин назад";
        }
        long hours = duration.toHours();
        return hours + " ч назад";
    }
}
