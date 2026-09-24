package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.service.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TeleportRequestCommand implements CommandExecutor, TabCompleter {

    public enum Mode {
        TPA,
        ACCEPT,
        DENY,
        TPA_HERE
    }

    private final Mode mode;
    private final TeleportService teleportService;
    private final MessageService messages;

    public TeleportRequestCommand(Mode mode, TeleportService teleportService, MessageService messages) {
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
            case TPA -> "foxaria.tpa";
            case ACCEPT -> "foxaria.tpaccept";
            case DENY -> "foxaria.tpdeny";
            case TPA_HERE -> "foxaria.tpahere";
        };
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        switch (mode) {
            case TPA -> {
                if (args.length < 1) {
                    messages.send(player, "teleport.usage.tpa", "&cИспользование: /tpa <игрок>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    messages.send(player, "general.player-not-found", "&cИгрок не найден.");
                    return true;
                }
                teleportService.sendTeleportRequest(player, target, false);
            }
            case TPA_HERE -> {
                if (args.length < 1) {
                    messages.send(player, "teleport.usage.tpahere", "&cИспользование: /tpahere <игрок>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    messages.send(player, "general.player-not-found", "&cИгрок не найден.");
                    return true;
                }
                teleportService.sendTeleportRequest(player, target, true);
            }
            case ACCEPT -> teleportService.acceptTeleportRequest(player);
            case DENY -> teleportService.denyTeleportRequest(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1 || (mode != Mode.TPA && mode != Mode.TPA_HERE) || !sender.hasPermission(mode == Mode.TPA ? "foxaria.tpa" : "foxaria.tpahere")) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
            .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
            .collect(Collectors.toList());
    }
}
