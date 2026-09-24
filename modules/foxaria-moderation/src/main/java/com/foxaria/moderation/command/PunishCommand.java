package com.foxaria.moderation.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import com.foxaria.core.command.StaffCommandInput;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.PunishmentCatalog;
import com.foxaria.moderation.PunishmentTextCodec;
import com.foxaria.moderation.util.ModerationTargetResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class PunishCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final JdbcModerationService moderationService;
    private final MessageService messages;
    private final PunishmentCatalog catalog;

    public PunishCommand(JavaPlugin plugin, JdbcModerationService moderationService, MessageService messages, PunishmentCatalog catalog) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.messages = messages;
        this.catalog = catalog;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] rawArgs) {
        if (!FoxariaStaffPermissions.has(sender, "foxaria.mod.punish")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        StaffCommandInput input = StaffCommandInput.parse(rawArgs);
        String[] args = input.args();
        boolean silent = input.silent();
        if (args.length < 2) {
            messages.send(sender, "mod.punish-usage", "&cИспользование: /punish <игрок> <код> [-s]");
            return true;
        }
        PunishmentCatalog.Entry entry = catalog.byCode(args[1]).orElse(null);
        if (entry == null) {
            messages.send(sender, "mod.punish-unknown-code", "&cКод причины не найден: <code>",
                new MessageService.Placeholder("code", args[1]));
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
            long expiresAt = entry.durationSeconds() > 0L ? System.currentTimeMillis() + (entry.durationSeconds() * 1000L) : 0L;
            moderationService.punish(sender, targetUuid, entry.type(), PunishmentTextCodec.encode(entry), expiresAt, silent);
            messages.send(sender, "mod.punish-applied",
                "&aНаказание <code> выдано игроку &f<player>&a.",
                new MessageService.Placeholder("code", entry.code()),
                new MessageService.Placeholder("player", args[0]));
        });
        return true;
    }
}
