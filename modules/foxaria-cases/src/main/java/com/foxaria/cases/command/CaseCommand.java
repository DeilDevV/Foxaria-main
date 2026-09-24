package com.foxaria.cases.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.cases.service.CaseService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class CaseCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;

    public CaseCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.cases.use") && !sender.hasPermission("foxaria.cases.admin")) {
            messages.send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        sender.sendMessage("§6Foxaria Cases §8» §7Подойдите к кейсу и нажмите §eПКМ§7.");
        sender.sendMessage("§7Админ: §f/caseadmin list §8| §f/caseadmin givekey §8| §f/caseadmin giveblock");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
