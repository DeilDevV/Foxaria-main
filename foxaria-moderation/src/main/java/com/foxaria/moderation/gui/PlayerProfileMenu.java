package com.foxaria.moderation.gui;

import com.foxaria.api.model.SecurityIncident;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.ModerationRepository;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class PlayerProfileMenu extends BaseMenu {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final UUID targetUuid;
    private final JdbcModerationService moderationService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final ModerationRepository.ReportEntry linkedReport;
    private final Consumer<Player> staffHomeBack;

    public PlayerProfileMenu(UUID targetUuid, JdbcModerationService moderationService, SecurityService securityService, AuditService auditService, ModerationRepository.ReportEntry linkedReport) {
        this(targetUuid, moderationService, securityService, auditService, linkedReport, null);
    }

    public PlayerProfileMenu(UUID targetUuid, JdbcModerationService moderationService, SecurityService securityService, AuditService auditService, ModerationRepository.ReportEntry linkedReport, Consumer<Player> staffHomeBack) {
        super("&8⟨ &5&lПрофиль &8⟩", 54);
        this.targetUuid = targetUuid;
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.linkedReport = linkedReport;
        this.staffHomeBack = staffHomeBack;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() == null ? targetUuid.toString() : target.getName();
        boolean frozen = moderationService.isFrozen(targetUuid);
        boolean muted = moderationService.isMuted(targetUuid);
        boolean underCheck = moderationService.isUnderCheck(targetUuid);

        setItem(13, MenuItems.head(
            target,
            "&f&l" + targetName,
            "&8───────────────",
            "&7Статус: " + state(onlineTarget != null && onlineTarget.isOnline(), "&a● в сети", "&c○ оффлайн"),
            "&7Проверка: " + state(underCheck, "&c● да", "&7○ нет"),
            "&7Заморозка: " + state(frozen, "&b● да", "&7○ нет"),
            "&7Мут: " + state(muted, "&e● да", "&7○ нет"),
            "&8───────────────",
            "&7ЛКМ по кнопкам: &fвидно персоналу",
            "&7ПКМ: &8тихий режим"
        ), null);

        setItem(10, MenuItems.item(
            org.bukkit.Material.SPYGLASS,
            "&6&lВызов на проверку",
            "&7Стандартный протокол проверки на читы.",
            "&8───────────────",
            "&eЛКМ &7обычный  &6ПКМ &7тихий",
            "&cПри выходе с сервера — санкция за уход"
        ), click -> {
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer == null) {
                sendMsg(viewer, "&cИгрок &f" + targetName + " &cдолжен быть в сети, чтобы начать проверку.");
                return;
            }
            moderationService.startCheck(viewer, targetPlayer, click.isRightClick());
        });

        setItem(11, MenuItems.item(
            org.bukkit.Material.LIME_DYE,
            "&a&lЗавершить проверку",
            "&7Игрок снова может спокойно играть.",
            "&eЛКМ &8/ &6ПКМ &7— с уведомлением или тихо"
        ), click -> moderationService.finishCheck(viewer, targetUuid, click.isRightClick(), "gui_finish"));

        setItem(12, MenuItems.item(
            org.bukkit.Material.BLUE_ICE,
            frozen ? "&b&lСнять заморозку" : "&b&lЗаморозить",
            "&7Лёд останавливает движение и взаимодействия.",
            "&7Игрок видит предупреждение в чате.",
            "&8───────────────",
            "&eЛКМ &8· &6ПКМ &7— публично или тихо"
        ), click -> {
            if (frozen) {
                moderationService.revoke(viewer, targetUuid, "gui_unfreeze", click.isRightClick(), "FREEZE");
            } else {
                moderationService.punish(viewer, targetUuid, "FREEZE", "Заморозка из панели модерации", 0L, click.isRightClick());
            }
        });

        setItem(14, MenuItems.item(
            org.bukkit.Material.IRON_BARS,
            muted ? "&e&lСнять мут" : "&e&lВыдать мут",
            "&7Ограничение чата до снятия или истечения.",
            "&eЛКМ &8· &6ПКМ &7— публично или тихо"
        ), click -> {
            if (muted) {
                moderationService.revoke(viewer, targetUuid, "gui_unmute", click.isRightClick(), "MUTE", "TEMPMUTE");
            } else {
                moderationService.punish(viewer, targetUuid, "MUTE", "Мут из панели модерации", 0L, click.isRightClick());
            }
        });

        setItem(15, MenuItems.item(
            org.bukkit.Material.WRITABLE_BOOK,
            "&c&lКаталог санкций",
            "&7Баны, муты и другие коды из &fmoderation.yml",
            "&7Срок и тип подставятся из каталога.",
            "&eЛКМ &8→ &7открыть список причин"
        ), click -> {
            PunishReasonMenu menu = new PunishReasonMenu(targetUuid, moderationService, v -> {
                PlayerProfileMenu back = new PlayerProfileMenu(targetUuid, moderationService, securityService, auditService, linkedReport, staffHomeBack);
                back.render(v);
                v.openInventory(back.inventory());
            });
            menu.render(viewer);
            viewer.openInventory(menu.inventory());
        });

        setItem(28, MenuItems.item(
            org.bukkit.Material.COMPASS,
            "&b&lТелепорт",
            "&7Мгновенно к координатам цели."
        ), click -> {
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer == null) {
                sendMsg(viewer, "&c" + targetName + " &7не в сети — телепорт недоступен.");
                return;
            }
            viewer.teleport(targetPlayer);
        });

        setItem(29, MenuItems.item(
            org.bukkit.Material.CHEST,
            "&6&lИнвентарь",
            "&7Живой просмотр рюкзака игрока."
        ), click -> {
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer == null) {
                sendMsg(viewer, "&c" + targetName + " &7не в сети.");
                return;
            }
            viewer.openInventory(targetPlayer.getInventory());
        });

        setItem(30, MenuItems.item(
            org.bukkit.Material.ENDER_CHEST,
            "&5&lЭндер-сундук",
            "&7Личное хранилище цели."
        ), click -> {
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer == null) {
                sendMsg(viewer, "&c" + targetName + " &7не в сети.");
                return;
            }
            viewer.openInventory(targetPlayer.getEnderChest());
        });

        setItem(32, MenuItems.item(
            org.bukkit.Material.BOOK,
            "&e&lИстория наказаний",
            "&7Последние записи в чат (тип и причина)."
        ), click -> moderationService.history(targetUuid).thenAccept(history ->
            sendList(viewer, "&eИстория наказаний", history.stream().map(record -> record.type() + " | " + record.reason()).toList())
        ));

        setItem(33, MenuItems.item(
            org.bukkit.Material.REDSTONE,
            "&c&lБезопасность",
            "&7Подозрительные события и флаги."
        ), click -> {
            if (securityService == null) {
                sendList(viewer, "&cБезопасность", List.of("&7Модуль сейчас недоступен."));
                return;
            }
            securityService.recent(targetUuid, 10).thenAccept(events ->
                sendList(viewer, "&cБезопасность", events.stream().map(SecurityIncident::summary).toList())
            );
        });

        setItem(34, MenuItems.item(
            org.bukkit.Material.WRITABLE_BOOK,
            "&d&lАудит",
            "&7Действия персонала вокруг этого игрока."
        ), click -> auditService.recent(targetUuid, 10).thenAccept(events ->
            sendList(viewer, "&dАудит", events.stream().map(event -> event.type() + " | " + event.summary()).toList())
        ));

        if (linkedReport != null) {
            OfflinePlayer reporter = Bukkit.getOfflinePlayer(linkedReport.reporterUuid());
            String reporterName = reporter.getName() == null ? linkedReport.reporterUuid().toString() : reporter.getName();
            setItem(38, MenuItems.item(
                org.bukkit.Material.PAPER,
                "&6&lАктивная жалоба",
                "&8───────────────",
                "&7Автор: &f" + reporterName,
                "&7Текст: &7" + linkedReport.reason()
            ), null);
            setItem(39, MenuItems.item(
                org.bukkit.Material.LIME_WOOL,
                "&a&lЗакрыть жалобу",
                "&7Отметить тикет как обработанный"
            ), click -> moderationService.closeReport(linkedReport.id(), viewer));
        }

        setItem(49, MenuItems.item(
            org.bukkit.Material.ARROW,
            "&7← &fНазад",
            staffHomeBack != null ? "&7К панели (админ / модерация)" : "&7К главной панели модерации"
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

    private String state(boolean enabled, String on, String off) {
        return enabled ? on : off;
    }

    private void sendMsg(Player viewer, String legacyAmpersand) {
        viewer.sendMessage(LEGACY.deserialize(legacyAmpersand));
    }

    private void sendList(Player viewer, String titleLegacy, List<String> lines) {
        viewer.getServer().getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            viewer.sendMessage(LEGACY.deserialize(titleLegacy + " &8—"));
            if (lines.isEmpty()) {
                sendMsg(viewer, "&7Записей пока нет.");
                return;
            }
            lines.forEach(line -> viewer.sendMessage(LEGACY.deserialize("&8▸ &7" + line)));
        });
    }
}
