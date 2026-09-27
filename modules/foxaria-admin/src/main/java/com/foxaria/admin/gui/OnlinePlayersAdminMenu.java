package com.foxaria.admin.gui;

import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ModerationService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.function.Consumer;

/**
 * Отдельное меню со списком онлайн-игроков.
 * Вынесено из AdminDashboardMenu, чтобы не захламлять основную панель.
 */
public final class OnlinePlayersAdminMenu extends BaseMenu {

    private final EconomyService economyService;
    private final ModerationService moderationService;
    private final SecurityService securityService;
    private final RankService rankService;
    private final AuditService auditService;
    private final Consumer<Player> back;

    public OnlinePlayersAdminMenu(EconomyService economyService, ModerationService moderationService,
                                  SecurityService securityService, RankService rankService,
                                  AuditService auditService, Consumer<Player> back) {
        super("&8⟨ &f&lОнлайн &8│ &7" + Bukkit.getOnlinePlayers().size() + " игроков &8⟩", 54);
        this.economyService = economyService;
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.rankService = rankService;
        this.auditService = auditService;
        this.back = back;
    }

    @Override
    protected void draw(Player viewer) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }

        setItem(4, MenuItems.item(
            Material.COMPASS,
            "&f&lИгроки онлайн",
            "&7Сейчас в сети: &f" + Bukkit.getOnlinePlayers().size(),
            "&7Нажми на голову для управления."
        ), null);

        int slot = 9;
        for (Player target : Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList()) {
            if (slot >= 45) break;
            setItem(slot++, MenuItems.head(
                target,
                "&f&l" + target.getName(),
                "&8───────────────",
                "&7Проверка: " + status(moderationService.isUnderCheck(target.getUniqueId())),
                "&7Мут: " + status(moderationService.isMuted(target.getUniqueId())),
                "&8───────────────",
                "&eЛКМ &7→ &7профиль: экономика, ранг, аудит"
            ), click -> {
                PlayerAdminMenu menu = new PlayerAdminMenu(target, economyService, moderationService,
                    securityService, rankService, auditService);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
        }

        if (slot == 9) {
            setItem(22, MenuItems.item(
                Material.LIME_STAINED_GLASS_PANE,
                "&a&lНикого нет в сети",
                "&7Карточки появятся автоматически при входе игрока."
            ), null);
        }

        setItem(45, MenuStyle.backButton(), click -> back.accept(viewer));

        setItem(49, MenuItems.item(
            Material.ENDER_EYE,
            "&d&lОбновить",
            "&7Обновить список онлайна"
        ), click -> {
            render(viewer);
            viewer.openInventory(inventory());
        });

        setItem(53, MenuStyle.closeButton(), click -> viewer.closeInventory());
    }

    private String status(boolean enabled) {
        return enabled ? "&c● да" : "&7○ нет";
    }
}
