package com.foxaria.admin.gui;

import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ModerationService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.gui.PunishReasonMenu;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

public final class PlayerAdminMenu extends BaseMenu {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Player target;
    private final EconomyService economyService;
    private final ModerationService moderationService;
    private final SecurityService securityService;
    private final RankService rankService;
    private final AuditService auditService;

    public PlayerAdminMenu(Player target, EconomyService economyService, ModerationService moderationService, SecurityService securityService, RankService rankService, AuditService auditService) {
        super("&8⟨ &4&lАдмин &8│ &f" + target.getName() + " &8⟩", 54);
        this.target = target;
        this.economyService = economyService;
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.rankService = rankService;
        this.auditService = auditService;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(13, MenuItems.head(
            target,
            "&f&l" + target.getName(),
            "&8───────────────",
            "&7Проверка: " + status(moderationService.isUnderCheck(target.getUniqueId())),
            "&7Заморозка: " + status(moderationService.isFrozen(target.getUniqueId())),
            "&7Мут: " + status(moderationService.isMuted(target.getUniqueId())),
            "&8───────────────",
            "&7ПКМ по санкциям: &8тихий режим"
        ), null);

        setItem(10, MenuItems.item(
            org.bukkit.Material.SPYGLASS,
            "&6&lПроверка",
            "&7Вызов на стандартный протокол.",
            "&eЛКМ &8· &6ПКМ &7тихий вызов"
        ), click -> moderationService.startCheck(viewer, target, click.isRightClick()));

        setItem(11, MenuItems.item(
            org.bukkit.Material.LIME_DYE,
            "&a&lЗавершить проверку",
            "&7Закрыть сессию и отпустить игрока.",
            "&eЛКМ &8· &6ПКМ &7тихо"
        ), click -> moderationService.finishCheck(viewer, target.getUniqueId(), click.isRightClick(), "admin_gui_finish"));

        setItem(12, MenuItems.item(
            org.bukkit.Material.BLUE_ICE,
            moderationService.isFrozen(target.getUniqueId()) ? "&b&lСнять лёд" : "&b&lЗаморозить",
            "&7Полная блокировка движения.",
            "&eЛКМ &8· &6ПКМ &7тихо"
        ), click -> {
            if (moderationService.isFrozen(target.getUniqueId())) {
                moderationService.revoke(viewer, target.getUniqueId(), "admin_panel_unfreeze", click.isRightClick(), "FREEZE");
            } else {
                moderationService.punish(viewer, target.getUniqueId(), "FREEZE", "Заморозка из админ-панели", 0L, click.isRightClick());
            }
        });

        setItem(14, MenuItems.item(
            org.bukkit.Material.IRON_BARS,
            moderationService.isMuted(target.getUniqueId()) ? "&e&lСнять мут" : "&e&lМут",
            "&7Ограничение чата.",
            "&eЛКМ &8· &6ПКМ &7тихо"
        ), click -> {
            if (moderationService.isMuted(target.getUniqueId())) {
                moderationService.revoke(viewer, target.getUniqueId(), "admin_panel_unmute", click.isRightClick(), "MUTE", "TEMPMUTE");
            } else {
                moderationService.punish(viewer, target.getUniqueId(), "MUTE", "Мут из админ-панели", 0L, click.isRightClick());
            }
        });

        setItem(15, MenuItems.item(
            org.bukkit.Material.TNT,
            "&c&lКик",
            "&7Мягкое отключение с сервера.",
            "&eЛКМ &8· &6ПКМ &7тихо"
        ), click -> moderationService.punish(viewer, target.getUniqueId(), "KICK", "Кик из админ-панели", 0L, click.isRightClick()));

        setItem(16, MenuItems.item(
            org.bukkit.Material.WRITABLE_BOOK,
            "&c&lКаталог санкций",
            "&7Коды из &fmoderation.yml &7— бан, мут, срок из конфига.",
            "&eЛКМ &8→ &7выбрать причину"
        ), click -> {
            if (!(moderationService instanceof JdbcModerationService jdbc)) {
                viewer.sendMessage(LEGACY.deserialize("&cНедоступно: модуль модерации не загружен."));
                return;
            }
            PunishReasonMenu menu = new PunishReasonMenu(target.getUniqueId(), jdbc, v -> {
                PlayerAdminMenu back = new PlayerAdminMenu(target, economyService, moderationService, securityService, rankService, auditService);
                back.render(v);
                v.openInventory(back.inventory());
            });
            menu.render(viewer);
            viewer.openInventory(menu.inventory());
        });

        setItem(28, MenuItems.item(
            org.bukkit.Material.GOLD_INGOT,
            "&6&lЭкономика ±100",
            "&7Мгновенная корректировка баланса.",
            "&eЛКМ &7выдать  &6ПКМ &7снять"
        ), click -> {
            if (click.isRightClick()) {
                economyService.withdraw(target.getUniqueId(), BigDecimal.valueOf(100), "admin_panel_take", viewer.getUniqueId());
                viewer.sendMessage(LEGACY.deserialize("&aСнято &f100 &aмон. у &f" + target.getName() + "&a."));
                return;
            }
            economyService.deposit(target.getUniqueId(), BigDecimal.valueOf(100), "admin_panel_grant", viewer.getUniqueId());
            viewer.sendMessage(LEGACY.deserialize("&aВыдано &f100 &aмон. игроку &f" + target.getName() + "&a."));
        });

        setItem(29, MenuItems.item(
            org.bukkit.Material.NAME_TAG,
            "&d&lБыстрый ранг",
            "&7Тестовые пресеты без полного /rank.",
            "&eЛКМ &7→ &fVIP  &6ПКМ &7→ &7default"
        ), click -> {
            if (click.isRightClick()) {
                rankService.setPrimaryGroup(target.getUniqueId(), "default");
                viewer.sendMessage(LEGACY.deserialize("&e" + target.getName() + " &7→ группа &fdefault&7."));
                return;
            }
            rankService.setPrimaryGroup(target.getUniqueId(), "vip");
            viewer.sendMessage(LEGACY.deserialize("&e" + target.getName() + " &7→ группа &fVIP&7."));
        });

        economyService.balance(target.getUniqueId()).thenAccept(snapshot ->
            target.getServer().getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () ->
                setItem(30, MenuItems.item(
                    org.bukkit.Material.EMERALD,
                    "&a&lБаланс",
                    "&7Сейчас: &f" + snapshot.balance(),
                    "&8Асинхронное обновление"
                ), null)
            )
        );

        rankService.primaryGroup(target.getUniqueId()).thenAccept(group ->
            target.getServer().getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () ->
                setItem(31, MenuItems.item(
                    org.bukkit.Material.BOOK,
                    "&b&lОсновной ранг",
                    "&7Группа: &f" + group
                ), null)
            )
        );

        setItem(32, MenuItems.item(
            org.bukkit.Material.WRITABLE_BOOK,
            "&d&lАудит",
            "&7Последние записи в чат"
        ), click -> auditService.recent(target.getUniqueId(), 10).thenAccept(events ->
            events.forEach(event -> viewer.sendMessage(LEGACY.deserialize("&8▸ &7" + event.type() + " &8| &f" + event.summary())))
        ));

        setItem(33, MenuItems.item(
            org.bukkit.Material.CHEST,
            "&6&lИнвентарь",
            "&7Прямой просмотр слотов"
        ), click -> viewer.openInventory(target.getInventory()));

        setItem(34, MenuItems.item(
            org.bukkit.Material.ENDER_CHEST,
            "&5&lЭндер-сундук",
            "&7Личное хранилище"
        ), click -> viewer.openInventory(target.getEnderChest()));

        setItem(40, MenuItems.item(
            org.bukkit.Material.COMPASS,
            "&b&lТелепорт",
            "&7Перейти к игроку"
        ), click -> viewer.teleport(target));

        setItem(49, MenuItems.item(
            org.bukkit.Material.ARROW,
            "&7← &fНазад",
            "&7К списку онлайна"
        ), click -> {
            AdminDashboardMenu menu = new AdminDashboardMenu(economyService, moderationService, securityService, rankService, auditService);
            menu.render(viewer);
            viewer.openInventory(menu.inventory());
        });
    }

    private String status(boolean enabled) {
        return enabled ? "&c● да" : "&7○ нет";
    }
}
