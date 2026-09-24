package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.service.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Случайный телепорт (/rtp).
 *
 * Общей точки спавна на гриферском сервере нет: /spawn и /setspawn удалены,
 * игрок попадает в случайную точку при первом входе и после смерти.
 */
public final class SpawnCommand implements CommandExecutor {

    public enum Mode {
        RTP
    }

    private final Mode mode;
    private final TeleportService teleportService;
    private final MessageService messages;

    public SpawnCommand(Mode mode, TeleportService teleportService, MessageService messages) {
        this.mode = mode;
        this.teleportService = teleportService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!sender.hasPermission("foxaria.rtp")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        teleportService.randomTeleport(player);
        return true;
    }
}
