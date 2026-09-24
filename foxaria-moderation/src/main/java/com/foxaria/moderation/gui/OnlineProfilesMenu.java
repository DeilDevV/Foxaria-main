package com.foxaria.moderation.gui;

import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.moderation.JdbcModerationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.function.Consumer;

public final class OnlineProfilesMenu extends BaseMenu {

    private final JdbcModerationService moderationService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final Consumer<Player> staffHomeBack;

    public OnlineProfilesMenu(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService) {
        this(moderationService, securityService, auditService, null);
    }

    public OnlineProfilesMenu(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService, Consumer<Player> staffHomeBack) {
        super("&8⟨ &b&lОнлайн &8⟩", 54);
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.staffHomeBack = staffHomeBack;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(4, MenuItems.item(
            org.bukkit.Material.COMPASS,
            "&b&lИгроки в сети",
            "&7Быстрый доступ к проверкам, заморозке и муту",
            "&eЛКМ по голове &8→ &7полный профиль"
        ), null);

        int slot = 10;
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
                "&eЛКМ &7открыть профиль"
            ), click -> {
                PlayerProfileMenu menu = new PlayerProfileMenu(target.getUniqueId(), moderationService, securityService, auditService, null, staffHomeBack);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
        }

        if (slot == 10) {
            setItem(22, MenuItems.item(
                org.bukkit.Material.LIME_STAINED_GLASS_PANE,
                "&a&lПустой сервер",
                "&7Когда кто-то зайдёт, карточки появятся здесь."
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
