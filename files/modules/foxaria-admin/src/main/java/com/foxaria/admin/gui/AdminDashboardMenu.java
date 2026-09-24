package com.foxaria.admin.gui;

import com.foxaria.admin.wipe.WipeService;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ModerationService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.function.Consumer;

public final class AdminDashboardMenu extends BaseMenu {

    private final EconomyService economyService;
    private final ModerationService moderationService;
    private final SecurityService securityService;
    private final RankService rankService;
    private final AuditService auditService;
    private final JavaPlugin plugin;
    private final WipeService wipeService;

    public AdminDashboardMenu(EconomyService economyService, ModerationService moderationService, SecurityService securityService, RankService rankService, AuditService auditService) {
        this(economyService, moderationService, securityService, rankService, auditService, null, null);
    }

    public AdminDashboardMenu(EconomyService economyService, ModerationService moderationService, SecurityService securityService,
                              RankService rankService, AuditService auditService, JavaPlugin plugin, WipeService wipeService) {
        super("&8⟨ &4&lАдмин &8⟩", 54);
        this.economyService = economyService;
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.rankService = rankService;
        this.auditService = auditService;
        this.plugin = plugin;
        this.wipeService = wipeService;
    }

    private Consumer<Player> adminHomeBack() {
        return v -> {
            AdminDashboardMenu menu = new AdminDashboardMenu(economyService, moderationService, securityService, rankService, auditService, plugin, wipeService);
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
            "&7Донат, экономика, сервер и персонал.",
            "&8───────────────",
            "&7Модерация вынесена в свою панель —",
            "&7здесь только админские инструменты."
        ), null);

        // ─── Разделы администрирования ───
        setItem(10, MenuItems.item(
            org.bukkit.Material.SUNFLOWER,
            "&6&lДонат",
            "&7Токены и донат-ранги игроков.",
            "&7Команды: &f/fdonate help",
            "&eЛКМ &8→ &7управление донатом"
        ), click -> {
            DonateAdminMenu menu = new DonateAdminMenu(economyService, rankService, auditService, adminHomeBack());
            menu.render(viewer);
            viewer.openInventory(menu.inventory());
        });

        setItem(12, MenuItems.item(
            org.bukkit.Material.GOLD_INGOT,
            "&e&lЭкономика и ранги",
            "&7Баланс, токены и группы — в карточке игрока.",
            "&7Команды: &f/eco&7, &f/token&7, &f/baltop",
            "&eЛКМ &8→ &7список игроков ниже"
        ), null);

        setItem(14, MenuItems.item(
            org.bukkit.Material.IRON_SWORD,
            "&c&lМодерация",
            "&7Жалобы, проверки и санкции.",
            "&7Полная панель: &f/modpanel",
            "&eЛКМ &8→ &7открыть панель модерации"
        ), click -> openModerationPanel(viewer));

        setItem(16, MenuItems.item(
            org.bukkit.Material.REDSTONE_TORCH,
            "&b&lСервер",
            "&7Техработы: &f/maintenance",
            "&7Рестарт с отсчётом: &f/restartcountdown",
            "&7Перечитать конфиги: &f/foxreload",
            "&8───────────────",
            "&eЛКМ &8→ &7перечитать конфиги"
        ), click -> {
            viewer.closeInventory();
            viewer.performCommand("foxreload");
        });

        setItem(28, MenuItems.item(
            org.bukkit.Material.KNOWLEDGE_BOOK,
            "&e&lСправочник команд",
            "&7Все команды Foxaria по разделам.",
            "&eЛКМ &8→ &7открыть справочник"
        ), click -> {
            CommandsReferenceMenu menu = new CommandsReferenceMenu(adminHomeBack());
            menu.render(viewer);
            viewer.openInventory(menu.inventory());
        });

        setItem(34, MenuItems.item(
            org.bukkit.Material.WRITABLE_BOOK,
            "&d&lЖурнал действий",
            "&7Что делал персонал: &f/auditlog",
            "&7Карточка игрока → раздел «Аудит»."
        ), null);

        // Раздел вайпа намеренно отделён и выделен красным:
        // это необратимые операции, случайный клик недопустим.
        if (wipeService != null && plugin != null) {
            setItem(32, MenuItems.item(
                org.bukkit.Material.TNT,
                "&4&l⚠ ВАЙП &8· &cосторожно",
                "&cНеобратимая очистка игровых данных.",
                "&8───────────────",
                "&7Приваты · гильдии · аукцион · монеты",
                "&7дома · прогресс · лавки игроков",
                "&8───────────────",
                "&aДонат и наказания не затрагиваются.",
                "&cЛКМ &8→ &7открыть раздел вайпа"
            ), click -> {
                WipeAdminMenu menu = new WipeAdminMenu(plugin, wipeService, adminHomeBack());
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
        }

        // ─── Игроки онлайн: админ-карточки ───
        setItem(30, MenuItems.item(
            org.bukkit.Material.COMPASS,
            "&f&lИгроки онлайн",
            "&7Сейчас в сети: &f" + Bukkit.getOnlinePlayers().size(),
            "&7Карточки ниже — админ-профиль игрока."
        ), null);

        int slot = 37;
        for (Player target : Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .toList()) {
            if (slot >= 44) {
                break;
            }
            setItem(slot++, MenuItems.head(
                target,
                "&f&l" + target.getName(),
                "&8───────────────",
                "&7Проверка: " + status(moderationService.isUnderCheck(target.getUniqueId())),
                "&7Мут: " + status(moderationService.isMuted(target.getUniqueId())),
                "&8───────────────",
                "&eЛКМ &7админ-профиль: экономика, ранг, аудит"
            ), click -> {
                PlayerAdminMenu menu = new PlayerAdminMenu(target, economyService, moderationService, securityService, rankService, auditService);
                menu.render(viewer);
                viewer.openInventory(menu.inventory());
            });
        }

        if (slot == 37) {
            setItem(40, MenuItems.item(
                org.bukkit.Material.LIME_STAINED_GLASS_PANE,
                "&a&lПусто",
                "&7Никого нет в сети — карточки",
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

        setItem(53, MenuStyle.closeButton(), click -> viewer.closeInventory());
    }

    private void openModerationPanel(Player viewer) {
        viewer.closeInventory();
        viewer.performCommand("modpanel");
    }

    private String status(boolean enabled) {
        return enabled ? "&c● да" : "&7○ нет";
    }
}
