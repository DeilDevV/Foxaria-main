package com.foxaria.customitems.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.customitems.ConfigCrateService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CrateCommand implements CommandExecutor {

    private final ConfigCrateService crateService;
    private final MessageService messages;

    public CrateCommand(ConfigCrateService crateService, MessageService messages) {
        this.crateService = crateService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!sender.hasPermission("foxaria.customitems.use")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length == 0) {
            crateService.openBrowser(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("preview") && args.length >= 2) {
            crateService.preview(player, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("open") && args.length >= 2) {
            crateService.openCrate(player, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("givekey") && args.length >= 3) {
            if (!player.hasPermission("foxaria.customitems.admin")) {
                messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            int amount;
            try {
                amount = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
            } catch (NumberFormatException exception) {
                messages.send(player, "economy.invalid-amount", "&cНекорректная сумма.");
                return true;
            }
            crateService.giveKey(target, args[2], amount, player.getUniqueId());
            return true;
        }
        crateService.openCrate(player, args[0]);
        return true;
    }
}
