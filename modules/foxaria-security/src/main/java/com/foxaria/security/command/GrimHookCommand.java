package com.foxaria.security.command;

import com.foxaria.security.JdbcSecurityService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

public final class GrimHookCommand implements CommandExecutor {

    private final JdbcSecurityService securityService;

    public GrimHookCommand(JdbcSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.security.grimhook")) {
            sender.sendMessage("У вас нет прав.");
            return true;
        }
        if (args.length < 3) {
            return true;
        }
        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetUuid = target == null ? null : target.getUniqueId();
        String check = args[2];
        String details = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
        switch (action) {
            case "alert" -> securityService.ingestGrimAlert(targetUuid, args[1], check, details);
            case "punish" -> securityService.ingestGrimPunish(targetUuid, args[1], check, details);
            default -> {
            }
        }
        return true;
    }
}
