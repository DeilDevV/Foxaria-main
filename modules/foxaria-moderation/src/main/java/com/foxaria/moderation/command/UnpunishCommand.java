package com.foxaria.moderation.command;

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

import java.util.UUID;

public final class UnpunishCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final JdbcModerationService moderationService;
    private final MessageService messages;

    public UnpunishCommand(JavaPlugin plugin, JdbcModerationService moderationService, MessageService messages) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] rawArgs) {
        if (!FoxariaStaffPermissions.has(sender, "foxaria.mod.unpunish")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        StaffCommandInput input = StaffCommandInput.parse(rawArgs);
        if (input.args().length < 1) {
            messages.send(sender, "mod.unpunish-usage", "&cИспользование: /unpunish <игрок> [-s]");
            return true;
        }
        ModerationTargetResolver.resolveUuid(plugin, input.args()[0], optionalUuid -> {
            if (optionalUuid.isEmpty()) {
                messages.send(sender, "mod.player-not-resolved", "&cНе удалось определить игрока &f<player>&c (ник не существует или Mojang недоступен).",
                    new MessageService.Placeholder("player", input.args()[0]));
                return;
            }
            UUID targetUuid = optionalUuid.get();
            if (sender instanceof Player actor && actor.getUniqueId().equals(targetUuid)) {
                messages.send(sender, "mod.self-target", "&cНельзя применять это действие к себе.");
                return;
            }
            moderationService.unpunishAll(sender, targetUuid, input.silent());
        });
        return true;
    }
}
