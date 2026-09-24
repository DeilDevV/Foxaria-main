package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.service.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomeCommand implements CommandExecutor, TabCompleter {

    public enum Mode {
        HOME,
        SET_HOME,
        DEL_HOME,
        HOMES
    }

    private final Mode mode;
    private final TeleportService teleportService;
    private final MessageService messages;

    public HomeCommand(Mode mode, TeleportService teleportService, MessageService messages) {
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
            case HOME -> "foxaria.home";
            case SET_HOME -> "foxaria.home.set";
            case DEL_HOME -> "foxaria.home.delete";
            case HOMES -> "foxaria.home.list";
        };
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        String homeName = args.length > 0 ? args[0].toLowerCase() : "main";
        switch (mode) {
            case HOME -> teleportService.teleportHome(player, homeName);
            case SET_HOME -> teleportService.setHome(player, homeName);
            case DEL_HOME -> teleportService.deleteHome(player, homeName);
            case HOMES -> teleportService.listHomes(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length > 1 || mode == Mode.HOMES || !sender.hasPermission("foxaria.home")) {
            return Collections.emptyList();
        }
        List<String> suggestions = new ArrayList<>();
        teleportService.cachedHomes(player.getUniqueId()).stream()
            .filter(name -> name.startsWith(args.length == 0 ? "" : args[0].toLowerCase()))
            .forEach(suggestions::add);
        return suggestions;
    }
}
