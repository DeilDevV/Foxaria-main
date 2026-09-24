package com.foxaria.modernfurnace.command;

import com.foxaria.modernfurnace.ModernFurnaceItems;
import com.foxaria.modernfurnace.ModernFurnaceKeys;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class AdminFurnacesCommand implements CommandExecutor {

    private final ModernFurnaceKeys keys;

    public AdminFurnacesCommand(ModernFurnaceKeys keys) {
        this.keys = keys;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.modernfurnace.admin")) {
            sender.sendMessage("§cНет прав.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§e/adminfurnaces give furnace [max] §7— себе");
            sender.sendMessage("§e/adminfurnaces give <ник> furnace [max]");
            sender.sendMessage("§e/adminfurnaces give key §7| §e/adminfurnaces give <ник> key");
            return true;
        }
        Player target;
        int typeIndex;
        Player online = Bukkit.getPlayer(args[1]);
        if (online != null && args.length >= 3) {
            target = online;
            typeIndex = 2;
        } else if (sender instanceof Player self) {
            target = self;
            typeIndex = 1;
        } else {
            sender.sendMessage("§cС консоли укажите игрока: /adminfurnaces give <ник> furnace [max]");
            return true;
        }
        if (args.length <= typeIndex) {
            sender.sendMessage("§cУкажите furnace или key.");
            return true;
        }
        String type = args[typeIndex].toLowerCase(Locale.ROOT);
        if (type.equals("key")) {
            target.getInventory().addItem(ModernFurnaceItems.key(keys));
            sender.sendMessage("§aВыдан ключ → §f" + target.getName());
            return true;
        }
        if (type.equals("furnace")) {
            boolean max = args.length > typeIndex + 1 && args[typeIndex + 1].equalsIgnoreCase("max");
            target.getInventory().addItem(ModernFurnaceItems.modernFurnace(keys, max));
            sender.sendMessage("§aВыдана печь → §f" + target.getName() + (max ? " §d(max)" : ""));
            return true;
        }
        sender.sendMessage("§cТип: §ffurnace §cили §fkey");
        return true;
    }
}
