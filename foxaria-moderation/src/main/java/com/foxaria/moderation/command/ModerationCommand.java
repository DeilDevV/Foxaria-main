package com.foxaria.moderation.command;

import com.foxaria.api.model.PunishmentRecord;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import com.foxaria.core.command.StaffCommandInput;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.util.ModerationTargetResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.UUID;

public final class ModerationCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final JdbcModerationService moderationService;
    private final MessageService messages;

    public ModerationCommand(JavaPlugin plugin, JdbcModerationService moderationService, MessageService messages) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] rawArgs) {
        String cmdName = command.getName().toLowerCase();
        String permission = switch (cmdName) {
            case "warn" -> "foxaria.mod.warn";
            case "mute", "unmute" -> "foxaria.mod.mute";
            case "tempmute" -> "foxaria.mod.tempmute";
            case "kick" -> "foxaria.mod.kick";
            case "ban", "unban" -> "foxaria.mod.ban";
            case "tempban" -> "foxaria.mod.tempban";
            case "freeze", "unfreeze" -> "foxaria.mod.freeze";
            case "history" -> "foxaria.mod.history";
            case "note" -> "foxaria.mod.note";
            case "report" -> "foxaria.report";
            case "check", "uncheck" -> "foxaria.mod.check";
            default -> "";
        };
        if (!permission.isBlank() && !FoxariaStaffPermissions.has(sender, permission)) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        StaffCommandInput input = StaffCommandInput.parse(rawArgs);
        String[] args = input.args();
        boolean silent = input.silent();

        if (cmdName.equals("report")) {
            handleReport(sender, args);
            return true;
        }
        if (args.length < 1) {
            sendUsage(sender, cmdName);
            return true;
        }

        if (cmdName.equals("freeze") || cmdName.equals("check")) {
            Player onlineTarget = ModerationTargetResolver.findOnlineByName(args[0]);
            if (onlineTarget == null) {
                messages.send(sender, "general.player-not-found", "&cИгрок не в сети (для этой команды нужен онлайн).");
                return true;
            }
            if (sender instanceof Player actor && actor.getUniqueId().equals(onlineTarget.getUniqueId())) {
                messages.send(sender, "mod.self-target", "&cНельзя применять это действие к себе.");
                return true;
            }
            if (cmdName.equals("freeze")) {
                handleFreeze(sender, onlineTarget, silent);
            } else {
                handleCheck(sender, onlineTarget, silent);
            }
            return true;
        }

        ModerationTargetResolver.resolveUuid(plugin, args[0], optionalUuid -> {
            if (optionalUuid.isEmpty()) {
                messages.send(sender, "mod.player-not-resolved", "&cНе удалось определить игрока &f<player>&c (ник не существует или Mojang недоступен).",
                    new MessageService.Placeholder("player", args[0]));
                return;
            }
            UUID targetUuid = optionalUuid.get();
            if (sender instanceof Player actor && actor.getUniqueId().equals(targetUuid)) {
                messages.send(sender, "mod.self-target", "&cНельзя применять это действие к себе.");
                return;
            }

            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Без причины";
            switch (cmdName) {
                case "warn" -> moderationService.punish(sender, targetUuid, "WARN", reason, 0L, silent);
                case "mute" -> moderationService.punish(sender, targetUuid, "MUTE", reason, 0L, silent);
                case "unmute" -> moderationService.revoke(sender, targetUuid, "manual_unmute", silent, "MUTE", "TEMPMUTE");
                case "tempmute" -> handleTimedMute(sender, args, targetUuid, silent);
                case "kick" -> moderationService.punish(sender, targetUuid, "KICK", reason, 0L, silent);
                case "ban" -> moderationService.punish(sender, targetUuid, "BAN", reason, 0L, silent);
                case "unban" -> moderationService.revoke(sender, targetUuid, "manual_unban", silent, "BAN", "TEMPBAN");
                case "tempban" -> handleTimedBan(sender, args, targetUuid, silent);
                case "unfreeze" -> moderationService.revoke(sender, targetUuid, "manual_unfreeze", silent, "FREEZE");
                case "uncheck" -> moderationService.finishCheck(sender, targetUuid, silent, "manual_finish");
                case "history" -> moderationService.history(targetUuid).thenAccept(records -> sendHistory(sender, records));
                case "note" -> {
                    if (sender instanceof Player player) {
                        moderationService.addNote(player, targetUuid, reason);
                        messages.send(sender, "mod.note-added", "&aЗаметка по игроку сохранена.");
                    } else {
                        messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
                    }
                }
                default -> {
                }
            }
        });
        return true;
    }

    private void handleTimedMute(CommandSender sender, String[] args, UUID targetUuid, boolean silent) {
        if (args.length < 2) {
            messages.send(sender, "mod.tempmute-usage", "&cИспользование: /tempmute <игрок> <секунды> [причина] [-s]");
            return;
        }
        try {
            long durationSeconds = Long.parseLong(args[1]);
            String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Временный мут";
            moderationService.punish(
                sender,
                targetUuid,
                "TEMPMUTE",
                reason,
                System.currentTimeMillis() + (durationSeconds * 1000L),
                silent
            );
        } catch (NumberFormatException exception) {
            messages.send(sender, "economy.invalid-amount", "&cНекорректное число.");
        }
    }

    private void handleTimedBan(CommandSender sender, String[] args, UUID targetUuid, boolean silent) {
        if (args.length < 2) {
            messages.send(sender, "mod.tempban-usage", "&cИспользование: /tempban <игрок> <секунды> [причина] [-s]");
            return;
        }
        try {
            long durationSeconds = Long.parseLong(args[1]);
            String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Временный бан";
            moderationService.punish(
                sender,
                targetUuid,
                "TEMPBAN",
                reason,
                System.currentTimeMillis() + (durationSeconds * 1000L),
                silent
            );
        } catch (NumberFormatException exception) {
            messages.send(sender, "economy.invalid-amount", "&cНекорректное число.");
        }
    }

    private void handleFreeze(CommandSender sender, Player onlineTarget, boolean silent) {
        if (moderationService.isFrozen(onlineTarget.getUniqueId())) {
            moderationService.revoke(sender, onlineTarget.getUniqueId(), "manual_unfreeze", silent, "FREEZE");
            return;
        }
        moderationService.punish(sender, onlineTarget.getUniqueId(), "FREEZE", "Заморозка администрацией", 0L, silent);
    }

    private void handleCheck(CommandSender sender, Player onlineTarget, boolean silent) {
        moderationService.startCheck(sender, onlineTarget, silent);
    }

    private void handleReport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player reporter) || args.length < 2) {
            messages.send(sender, "mod.report-usage", "&cИспользование: /report <игрок> <причина>");
            return;
        }
        Player target = ModerationTargetResolver.findOnlineByName(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", "&cИгрок не найден.");
            return;
        }
        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            messages.send(sender, "mod.self-target", "&cНельзя применять это действие к себе.");
            return;
        }
        moderationService.report(reporter, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        messages.send(reporter, "mod.report-sent", "&aЖалоба на игрока <player> отправлена.", new MessageService.Placeholder("player", target.getName()));
    }

    private void sendHistory(CommandSender sender, java.util.List<PunishmentRecord> records) {
        String history = records.isEmpty()
            ? "записей нет"
            : records.stream().map(record -> record.type() + ": " + record.reason()).reduce((left, right) -> left + " | " + right).orElse("записей нет");
        messages.send(sender, "mod.history", "&eИстория: <history>", new MessageService.Placeholder("history", history));
    }

    private void sendUsage(CommandSender sender, String name) {
        String fallback = switch (name) {
            case "warn" -> "&cИспользование: /warn <игрок> [причина] [-s]";
            case "mute" -> "&cИспользование: /mute <игрок> [причина] [-s]";
            case "unmute" -> "&cИспользование: /unmute <игрок> [-s]";
            case "tempmute" -> "&cИспользование: /tempmute <игрок> <секунды> [причина] [-s]";
            case "kick" -> "&cИспользование: /kick <игрок> [причина] [-s]";
            case "ban" -> "&cИспользование: /ban <игрок> [причина] [-s]";
            case "unban" -> "&cИспользование: /unban <игрок> [-s]";
            case "tempban" -> "&cИспользование: /tempban <игрок> <секунды> [причина] [-s]";
            case "freeze" -> "&cИспользование: /freeze <игрок> [-s]";
            case "unfreeze" -> "&cИспользование: /unfreeze <игрок> [-s]";
            case "check" -> "&cИспользование: /check <игрок> [-s]";
            case "uncheck" -> "&cИспользование: /uncheck <игрок> [-s]";
            case "history" -> "&cИспользование: /history <игрок>";
            case "note" -> "&cИспользование: /note <игрок> <текст>";
            default -> "&cНедостаточно аргументов.";
        };
        messages.send(sender, "mod.usage", fallback);
    }
}
