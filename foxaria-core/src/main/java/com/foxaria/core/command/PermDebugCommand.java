package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.service.FoxariaPermissionService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PermDebugCommand implements CommandExecutor {

    private final FoxariaPermissionService permissionService;
    private final MessageService messages;

    public PermDebugCommand(FoxariaPermissionService permissionService, MessageService messages) {
        this.permissionService = permissionService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.permdebug")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "general.player-not-found", "&cИгрок не найден.");
                return true;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages.send(sender, "general.players-only", "&cУкажите игрока: /permdebug <ник>");
            return true;
        }

        String group = permissionService.debugGroup(target);
        sender.sendMessage("§6[PermDebug] §fИгрок: §e" + target.getName());
        sender.sendMessage("§6[PermDebug] §fГруппа: §b" + group);
        sender.sendMessage("§6[PermDebug] §fПрава (" + permissionService.debugPermissions(target).size() + "):");
        for (String node : permissionService.debugPermissions(target)) {
            sender.sendMessage(" §8- §a" + node);
        }
        return true;
    }
}
