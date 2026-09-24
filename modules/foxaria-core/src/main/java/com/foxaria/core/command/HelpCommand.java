package com.foxaria.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class HelpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> lines = new ArrayList<>();
        lines.add("§6§lFOXARIA §8» §fДоступные команды:");
        lines.add(" §e/help §8- §7этот список");
        lines.add(" §e/menu §8- §7главное меню");
        lines.add(" §e/server §8- §7выбор сервера");
        lines.add(" §e/spawn §8- §7телепортация на спавн");
        lines.add(" §e/home, /sethome, /delhome, /homes");
        lines.add(" §e/tpa, /tpaccept, /tpdeny, /tpahere");
        lines.add(" §e/bal, /pay, /baltop, /token");
        lines.add(" §e/shop, /donateshop, /kits, /kit");
        lines.add(" §e/quest, /rewards, /streak, /season");
        lines.add(" §e/guild, /region, /craft");

        if (sender.hasPermission("foxaria.permdebug")) {
            lines.add(" §6Админ: §e/permdebug");
        }
        if (sender.hasPermission("foxaria.economy.admin")) {
            lines.add(" §6Админ: §e/eco");
        }
        if (sender.hasPermission("foxaria.region.admin")) {
            lines.add(" §6Админ: §e/regionadmin");
        }
        if (sender.hasPermission("foxaria.admin.reload")) {
            lines.add(" §6Админ: §e/foxreload");
        }

        if (sender instanceof Player) {
            sender.sendMessage(String.join("\n", lines));
        } else {
            lines.add(" §6Консоль: используйте команды модулей и /permdebug <ник>");
            sender.sendMessage(String.join("\n", lines));
        }
        return true;
    }
}
