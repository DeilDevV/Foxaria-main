package com.foxaria.moderation;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.model.PunishmentRecord;
import com.foxaria.api.model.StaffCheckSession;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ModerationService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class JdbcModerationService implements ModerationService {

    private final JavaPlugin plugin;
    private final ModerationRepository repository;
    private final AuditService audits;
    private final MessageService messages;
    private final FileConfiguration config;
    private final Map<UUID, List<PunishmentRecord>> activeCache = new ConcurrentHashMap<>();
    private final Map<UUID, StaffCheckSession> activeChecks = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> socialSpy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> commandSpy = ConcurrentHashMap.newKeySet();

    public JdbcModerationService(JavaPlugin plugin, ModerationRepository repository, AuditService audits, MessageService messages, FileConfiguration config) {
        this.plugin = plugin;
        this.repository = repository;
        this.audits = audits;
        this.messages = messages;
        this.config = config;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public CompletableFuture<Void> punish(CommandSender actor, UUID targetUuid, String type, String reason, long expiresAt, boolean silent) {
        if (isSelfTarget(actor, targetUuid) && isSelfRestricted(type)) {
            messages.send(actor, "mod.self-target", "&cНельзя применять это действие к себе.");
            return CompletableFuture.completedFuture(null);
        }
        PunishmentRecord record = new PunishmentRecord(
            UUID.randomUUID().toString(),
            targetUuid,
            actor instanceof Player player ? player.getUniqueId() : null,
            type,
            reason,
            System.currentTimeMillis(),
            expiresAt,
            isActiveType(type)
        );
        if (type.equalsIgnoreCase("BAN") || type.equalsIgnoreCase("TEMPBAN")) {
            activeChecks.remove(targetUuid);
        }
        return repository.addPunishment(record).thenCompose(ignored -> refresh(targetUuid)).thenRun(() -> runSync(() -> {
            audits.append(new AuditEvent(
                "PUNISHMENT_CREATED",
                actor instanceof Player player ? player.getUniqueId() : null,
                targetUuid,
                actor.getName(),
                targetUuid.toString(),
                "Punishment created",
                Map.of("type", type, "reason", reason, "silent", String.valueOf(silent)),
                System.currentTimeMillis()
            ));
            String actorLabel = actor instanceof Player p ? p.getName() : (actor.getName() != null ? actor.getName() : "Консоль");
            if (!silent && PunishmentBroadcasts.shouldAnnounce(type)) {
                PunishmentBroadcasts.NetworkAnnouncement ann = PunishmentBroadcasts.buildPunishNetworkAnnouncement(
                    record,
                    actorLabel,
                    exp -> exp <= 0L ? "перманентно" : humanExpiry(exp)
                );
                boolean sent = ProxyNetworkAnnounce.tryBroadcast(plugin, ann.mainLegacy(), ann.hoverLegacyMultiline());
                if (!sent) {
                    Component line = PunishmentBroadcasts.buildPublicLine(record, actorLabel, exp -> exp <= 0L ? "перманентно" : humanExpiry(exp));
                    Bukkit.broadcast(line);
                }
            }
            Player target = Bukkit.getPlayer(targetUuid);
            if (target == null) {
                return;
            }
            switch (type.toUpperCase()) {
                case "WARN" -> {
                    if (!silent) {
                        PunishmentTextCodec.Parsed parsed = PunishmentTextCodec.parse(reason);
                        messages.send(target, "mod.warn", "&cПредупреждение: <reason>", new MessageService.Placeholder("reason", parsed.description()));
                    }
                }
                case "MUTE", "TEMPMUTE" -> {
                    if (!silent) {
                        PunishmentTextCodec.Parsed parsed = PunishmentTextCodec.parse(reason);
                        messages.send(target, "mod.muted-detailed",
                            "&cВам выдан мут: &f<reason>&c. Срок: &f<expires>",
                            new MessageService.Placeholder("reason", parsed.description()),
                            new MessageService.Placeholder("expires", humanExpiry(expiresAt)));
                    }
                }
                case "KICK", "BAN", "TEMPBAN" -> target.kick(renderPunishmentComponent(record, actor.getName()));
                case "FREEZE" -> {
                    if (!silent) {
                        messages.send(target, "mod.freeze", "&cВы заморожены администрацией.");
                    }
                }
                default -> {
                }
            }
        }));
    }

    /**
     * Снимает все активные санкции из БД (мут, бан, заморозка и т.д.).
     */
    public CompletableFuture<Void> unpunishAll(CommandSender actor, UUID targetUuid, boolean silent) {
        if (isSelfTarget(actor, targetUuid)) {
            messages.send(actor, "mod.self-target", "&cНельзя применять это действие к себе.");
            return CompletableFuture.completedFuture(null);
        }
        return repository.active(targetUuid).thenCompose(records -> {
            List<PunishmentRecord> activeList = records.stream().filter(this::isActiveRecord).toList();
            if (activeList.isEmpty()) {
                runSync(() -> messages.send(actor, "mod.unpunish-nothing", "&cУ игрока нет активных санкций."));
                return CompletableFuture.completedFuture(null);
            }
            String removerLabel = actor instanceof Player p ? p.getName() : (actor.getName() != null ? actor.getName() : "Консоль");
            return repository.deactivateTypes(targetUuid, "MUTE", "TEMPMUTE", "BAN", "TEMPBAN", "FREEZE")
                .thenCompose(ignored -> refresh(targetUuid))
                .thenRun(() -> runSync(() -> {
                    audits.append(new AuditEvent(
                        "UNPUNISH_ALL",
                        actor instanceof Player player ? player.getUniqueId() : null,
                        targetUuid,
                        actor.getName(),
                        targetUuid.toString(),
                        "All punishments cleared",
                        Map.of("silent", String.valueOf(silent)),
                        System.currentTimeMillis()
                    ));
                    if (!silent) {
                        PunishmentBroadcasts.NetworkAnnouncement ann = PunishmentBroadcasts.buildUnpunishNetworkAnnouncement(targetUuid, activeList, removerLabel);
                        boolean sent = ProxyNetworkAnnounce.tryBroadcast(plugin, ann.mainLegacy(), ann.hoverLegacyMultiline());
                        if (!sent) {
                            Component line = PunishmentBroadcasts.buildUnpunishAnnouncement(targetUuid, activeList, removerLabel);
                            Bukkit.broadcast(line);
                        }
                    }
                    OfflinePlayer off = Bukkit.getOfflinePlayer(targetUuid);
                    String name = off.getName() == null || off.getName().isBlank() ? targetUuid.toString() : off.getName();
                    messages.send(actor, "mod.unpunish-ok", "&aВсе активные санкции сняты с &f<player>&a.",
                        new MessageService.Placeholder("player", name));
                    Player target = Bukkit.getPlayer(targetUuid);
                    if (target != null && !silent) {
                        messages.send(target, "mod.restrictions-lifted",
                            "&7[&6FOXARIA&7] &aС вас сняты ограничения в чате и другие активные санкции.");
                    }
                }));
        });
    }

    private boolean isActiveRecord(PunishmentRecord record) {
        if (!record.active()) {
            return false;
        }
        if (record.expiresAt() > 0L && record.expiresAt() < System.currentTimeMillis()) {
            return false;
        }
        return true;
    }

    @Override
    public CompletableFuture<Void> revoke(CommandSender actor, UUID targetUuid, String reason, boolean silent, String... types) {
        return repository.deactivateTypes(targetUuid, types).thenCompose(ignored -> refresh(targetUuid)).thenRun(() -> runSync(() -> {
            audits.append(new AuditEvent(
                "PUNISHMENT_REVOKED",
                actor instanceof Player player ? player.getUniqueId() : null,
                targetUuid,
                actor.getName(),
                targetUuid.toString(),
                "Punishment revoked",
                Map.of("types", String.join(",", types), "reason", reason, "silent", String.valueOf(silent)),
                System.currentTimeMillis()
            ));
            Player target = Bukkit.getPlayer(targetUuid);
            if (target == null || silent) {
                return;
            }
            boolean chat = false;
            boolean ban = false;
            boolean freeze = false;
            for (String type : types) {
                switch (type.toUpperCase()) {
                    case "MUTE", "TEMPMUTE" -> chat = true;
                    case "BAN", "TEMPBAN" -> ban = true;
                    case "FREEZE" -> freeze = true;
                    default -> {
                    }
                }
            }
            int kinds = (chat ? 1 : 0) + (ban ? 1 : 0) + (freeze ? 1 : 0);
            if (kinds > 1 || (chat && ban) || (chat && freeze) || (ban && freeze)) {
                messages.send(target, "mod.restrictions-lifted",
                    "&7[&6FOXARIA&7] &aС вас сняты ограничения в чате и другие активные санкции.");
            } else if (chat) {
                messages.send(target, "mod.unmute", "&7[&6FOXARIA&7] &aС вас сняты ограничения в чате.");
            } else if (ban) {
                messages.send(target, "mod.unban", "&7[&6FOXARIA&7] &aВаша блокировка снята. Снова можете зайти на сервер.");
            } else if (freeze) {
                messages.send(target, "mod.unfreeze", "&7[&6FOXARIA&7] &aВы больше не заморожены.");
            }
        }));
    }

    @Override
    public CompletableFuture<Void> startCheck(CommandSender actor, Player target, boolean silent) {
        if (isSelfTarget(actor, target.getUniqueId())) {
            messages.send(actor, "mod.self-target", "&cНельзя применять это действие к себе.");
            return CompletableFuture.completedFuture(null);
        }
        if (activeChecks.containsKey(target.getUniqueId())) {
            messages.send(actor, "mod.check-already", "&cИгрок уже находится на проверке.");
            return CompletableFuture.completedFuture(null);
        }

        UUID actorUuid = actor instanceof Player player ? player.getUniqueId() : null;
        StaffCheckSession session = new StaffCheckSession(target.getUniqueId(), actorUuid, actor.getName(), System.currentTimeMillis(), silent);
        activeChecks.put(target.getUniqueId(), session);

        audits.append(new AuditEvent(
            "CHECK_STARTED",
            actorUuid,
            target.getUniqueId(),
            actor.getName(),
            target.getName(),
            "Staff check started",
            Map.of("silent", String.valueOf(silent)),
            System.currentTimeMillis()
        ));

        if (actor instanceof Player staff && staff.isOnline()) {
            staff.teleport(target);
        }

        if (!silent) {
            messages.send(target, "mod.check-called-target", "&cВы вызваны на проверку. Не выходите с сервера, иначе получите бан за уход от проверки на читы.");
        }
        messages.send(actor, "mod.check-called-staff", "&aИгрок <player> вызван на проверку.", new MessageService.Placeholder("player", target.getName()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> finishCheck(CommandSender actor, UUID targetUuid, boolean silent, String reason) {
        StaffCheckSession removed = activeChecks.remove(targetUuid);
        if (removed == null) {
            messages.send(actor, "mod.check-missing", "&cИгрок сейчас не находится на проверке.");
            return CompletableFuture.completedFuture(null);
        }
        audits.append(new AuditEvent(
            "CHECK_FINISHED",
            actor instanceof Player player ? player.getUniqueId() : null,
            targetUuid,
            actor.getName(),
            targetUuid.toString(),
            "Staff check finished",
            Map.of("silent", String.valueOf(silent), "reason", reason),
            System.currentTimeMillis()
        ));
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && !silent) {
            messages.send(target, "mod.check-cleared-target", "&aПроверка завершена. Вы свободны.");
        }
        messages.send(actor, "mod.check-cleared-staff", "&aПроверка игрока завершена.");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isUnderCheck(UUID playerUuid) {
        return activeChecks.containsKey(playerUuid);
    }

    @Override
    public List<StaffCheckSession> activeChecks() {
        return List.copyOf(activeChecks.values());
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> history(UUID targetUuid) {
        return repository.history(targetUuid);
    }

    @Override
    public boolean isMuted(UUID playerUuid) {
        return activeCache.getOrDefault(playerUuid, List.of()).stream().anyMatch(record -> isCurrent(record, "MUTE", "TEMPMUTE"));
    }

    @Override
    public boolean isFrozen(UUID playerUuid) {
        return activeCache.getOrDefault(playerUuid, List.of()).stream().anyMatch(record -> isCurrent(record, "FREEZE"));
    }

    public CompletableFuture<List<PunishmentRecord>> refresh(UUID playerUuid) {
        return repository.active(playerUuid).thenApply(records -> {
            activeCache.put(playerUuid, records);
            return records;
        });
    }

    public CompletableFuture<Void> report(Player reporter, Player target, String reason) {
        return repository.addReport(reporter.getUniqueId(), target.getUniqueId(), reason).thenRun(() -> audits.append(new AuditEvent(
            "REPORT_CREATED",
            reporter.getUniqueId(),
            target.getUniqueId(),
            reporter.getName(),
            target.getName(),
            "Report created",
            Map.of("reason", reason),
            System.currentTimeMillis()
        )));
    }

    public CompletableFuture<Void> addNote(Player actor, UUID targetUuid, String note) {
        return repository.addNote(actor.getUniqueId(), targetUuid, note);
    }

    public CompletableFuture<List<ModerationRepository.ReportEntry>> openReports() {
        return repository.openReports();
    }

    public CompletableFuture<Void> closeReport(String reportId, Player actor) {
        return repository.closeReport(reportId, actor.getUniqueId()).thenRun(() -> audits.append(new AuditEvent(
            "REPORT_CLOSED",
            actor.getUniqueId(),
            null,
            actor.getName(),
            null,
            "Report closed",
            Map.of("reportId", reportId),
            System.currentTimeMillis()
        )));
    }

    public CompletableFuture<Void> toggleFreeze(CommandSender actor, Player target) {
        if (isSelfTarget(actor, target.getUniqueId())) {
            messages.send(actor, "mod.self-target", "&cНельзя применять это действие к себе.");
            return CompletableFuture.completedFuture(null);
        }
        if (isFrozen(target.getUniqueId())) {
            return revoke(actor, target.getUniqueId(), "freeze_toggle", false, "FREEZE");
        }
        return punish(actor, target.getUniqueId(), "FREEZE", "Заморозка администрацией", 0L, false);
    }

    public CompletableFuture<Void> toggleMute(CommandSender actor, UUID targetUuid) {
        if (isSelfTarget(actor, targetUuid)) {
            messages.send(actor, "mod.self-target", "&cНельзя применять это действие к себе.");
            return CompletableFuture.completedFuture(null);
        }
        if (isMuted(targetUuid)) {
            return revoke(actor, targetUuid, "mute_toggle", false, "MUTE", "TEMPMUTE");
        }
        return punish(actor, targetUuid, "MUTE", "Мут администрацией", 0L, false);
    }

    public void handleQuit(Player quitter) {
        StaffCheckSession session = activeChecks.remove(quitter.getUniqueId());
        if (session != null) {
            punish(plugin.getServer().getConsoleSender(), quitter.getUniqueId(), "BAN", "Уход от проверки на читы", 0L, true);
            Player actor = session.actorUuid() == null ? null : Bukkit.getPlayer(session.actorUuid());
            if (actor != null) {
                messages.send(actor, "mod.check-left-ban", "&cИгрок <player> вышел с сервера и автоматически забанен за уход от проверки.", new MessageService.Placeholder("player", quitter.getName()));
            }
            audits.append(new AuditEvent(
                "CHECK_LEFT_BAN",
                session.actorUuid(),
                quitter.getUniqueId(),
                session.actorName(),
                quitter.getName(),
                "Target left during staff check",
                Map.of("reason", "Уход от проверки на читы"),
                System.currentTimeMillis()
            ));
        }

        activeChecks.entrySet().removeIf(entry -> {
            StaffCheckSession value = entry.getValue();
            if (value.actorUuid() == null || !value.actorUuid().equals(quitter.getUniqueId())) {
                return false;
            }
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target != null && !value.silent()) {
                messages.send(target, "mod.check-cleared-target", "&aПроверка завершена. Вы свободны.");
            }
            return true;
        });
    }

    public PunishmentCatalog punishmentCatalog() {
        return new PunishmentCatalog(config);
    }

    public void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public Set<UUID> vanished() {
        return vanished;
    }

    public Set<UUID> socialSpy() {
        return socialSpy;
    }

    public Set<UUID> commandSpy() {
        return commandSpy;
    }

    public PunishmentRecord activeBanRecord(UUID playerUuid) {
        return activeCache.getOrDefault(playerUuid, List.of()).stream()
            .filter(record -> isCurrent(record, "BAN", "TEMPBAN"))
            .findFirst()
            .orElse(null);
    }

    public boolean toggleVanish(Player player) {
        UUID id = player.getUniqueId();
        if (vanished.add(id)) {
            refreshVanishVisibility();
            return true;
        }
        vanished.remove(id);
        refreshVanishVisibility();
        return false;
    }

    /**
     * Единый пересчёт vanish-состояния для всех игроков.
     * Если цель vanished, её видят только она сама и staff с foxaria.mod.vanish.see.
     */
    public void refreshVanishVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean canSeeVanished = FoxariaStaffPermissions.has(viewer, "foxaria.mod.vanish.see");
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) {
                    continue;
                }
                boolean targetVanished = vanished.contains(target.getUniqueId());
                if (targetVanished && !canSeeVanished) {
                    viewer.hidePlayer(plugin, target);
                    viewer.unlistPlayer(target);
                } else {
                    viewer.showPlayer(plugin, target);
                    viewer.listPlayer(target);
                }
            }
        }
    }

    private boolean isActiveType(String type) {
        return switch (type.toUpperCase()) {
            case "MUTE", "TEMPMUTE", "BAN", "TEMPBAN", "FREEZE" -> true;
            default -> false;
        };
    }

    private boolean isCurrent(PunishmentRecord record, String... types) {
        for (String type : types) {
            if (!record.type().equalsIgnoreCase(type)) {
                continue;
            }
            if (record.expiresAt() > 0 && record.expiresAt() < System.currentTimeMillis()) {
                return false;
            }
            return record.active();
        }
        return false;
    }

    private boolean isSelfTarget(CommandSender actor, UUID targetUuid) {
        return actor instanceof Player player && player.getUniqueId().equals(targetUuid);
    }

    private boolean isSelfRestricted(String type) {
        return switch (type.toUpperCase()) {
            case "WARN", "MUTE", "TEMPMUTE", "KICK", "BAN", "TEMPBAN", "FREEZE" -> true;
            default -> false;
        };
    }

    public String renderPunishmentPlain(PunishmentRecord record) {
        return renderPunishmentString(record, resolveActorName(record.actorUuid()));
    }

    private Component renderPunishmentComponent(PunishmentRecord record, String fallbackActorName) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(renderPunishmentString(record, fallbackActorName));
    }

    private String renderPunishmentString(PunishmentRecord record, String actorName) {
        PunishmentTextCodec.Parsed parsed = PunishmentTextCodec.parse(record.reason());
        String template = config.getString("punishments.screen.message",
            "&6&lFOXARIA\n%block_notice%\n&cПричина: &f%reason_title%\n&7%reason_description%\n&eСрок: &f%expires%\n&cВыдал: &f%actor%");
        return template
            .replace("%block_notice%", punishmentBlockNoticeLine(record.type()))
            .replace("%reason_code%", parsed.code())
            .replace("%reason_title%", parsed.title())
            .replace("%reason_description%", parsed.description())
            .replace("%expires%", humanExpiry(record.expiresAt()))
            .replace("%actor%", actorName == null || actorName.isBlank() ? "Администрация" : actorName);
    }

    private static String punishmentBlockNoticeLine(String type) {
        if (type != null && "KICK".equalsIgnoreCase(type)) {
            return "&eВас отключили от сети";
        }
        return "&cВы были заблокированы";
    }

    private String resolveActorName(UUID actorUuid) {
        if (actorUuid == null) {
            return "Администрация";
        }
        Player online = Bukkit.getPlayer(actorUuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(actorUuid);
        return offline.getName() == null || offline.getName().isBlank() ? "Администрация" : offline.getName();
    }

    private String humanExpiry(long expiresAt) {
        if (expiresAt <= 0L) {
            return "Навсегда";
        }
        long remaining = Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
        if (remaining >= 86400L && remaining % 86400L == 0L) {
            return (remaining / 86400L) + "д";
        }
        if (remaining >= 3600L && remaining % 3600L == 0L) {
            return (remaining / 3600L) + "ч";
        }
        if (remaining >= 60L && remaining % 60L == 0L) {
            return (remaining / 60L) + "м";
        }
        return remaining + "с";
    }
}
