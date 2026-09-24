package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpeedCommand implements CommandExecutor {

    private final MessageService messages;

    public SpeedCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!player.hasPermission("foxaria.speed")) {
            messages.send(player, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "ui.speed-usage", "&cИспользование: /speed <1-10>");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            messages.send(player, "ui.speed-usage", "&cИспользование: /speed <1-10>");
            return true;
        }

        if (level < 1 || level > 10) {
            messages.send(player, "ui.speed-usage", "&cИспользование: /speed <1-10>");
            return true;
        }

        float walkSpeed = 0.2F + ((level - 1) * (0.8F / 9.0F));
        float flySpeed = 0.1F + ((level - 1) * (0.9F / 9.0F));
        player.setWalkSpeed(Math.min(1.0F, walkSpeed));
        player.setFlySpeed(Math.min(1.0F, flySpeed));
        player.sendActionBar(messages.component(
            "ui.speed-set-actionbar",
            "&aСкорость: уровень <level>.",
            new MessageService.Placeholder("level", String.valueOf(level))
        ));
        messages.send(
            player,
            "ui.speed-set",
            "&aСкорость установлена на уровень <level>.",
            new MessageService.Placeholder("level", String.valueOf(level))
        );
        return true;
    }
}
