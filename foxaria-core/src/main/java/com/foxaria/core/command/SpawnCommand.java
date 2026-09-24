package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.service.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnCommand implements CommandExecutor {

    public enum Mode {
        SPAWN,
        SET_SPAWN,
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

        String permission = switch (mode) {
            case SPAWN -> "foxaria.spawn";
            case SET_SPAWN -> "foxaria.setspawn";
            case RTP -> "foxaria.rtp";
        };
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        switch (mode) {
            case SPAWN -> teleportService.teleportSpawn(player);
            case SET_SPAWN -> teleportService.setSpawn(player, player.getLocation());
            case RTP -> teleportService.randomTeleport(player);
        }
        return true;
    }
}
