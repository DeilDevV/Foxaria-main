package com.foxaria.guilds.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.guilds.GuildService;
import com.foxaria.guilds.GuildRepository;
import com.foxaria.guilds.GuildWarEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;

public final class GuildCommand implements CommandExecutor, TabCompleter {
    private final GuildService guilds;
    private final GuildWarEngine wars;
    private final MessageService messages;

    public GuildCommand(GuildService guilds, GuildWarEngine wars, MessageService messages) {
        this.guilds = guilds;
        this.wars = wars;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("wararena")) {
            return handleWarArenaAdmin(sender, args);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!player.hasPermission("foxaria.guild.use")) {
            messages.send(player, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length == 0) {
            guilds.openEntry(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "info" -> {
                if (args.length < 2) {
                     messages.send(player, "guild.info-usage", "&cИспользование: /guild info <название>");
                     return true;
                }
                guilds.openGuildInfo(player, args[1]);
                return true;
            }
            case "disband" -> {
                guilds.disband(player);
                return true;
            }
            case "create" -> {
                if (args.length < 2) return true;
                guilds.create(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                return true;
            }
            case "invite" -> {
                if (args.length < 2) return true;
                guilds.invite(player, args[1]);
                return true;
            }
            case "accept" -> {
                guilds.acceptInvite(player);
                return true;
            }
            case "kick" -> {
                if (args.length < 2) return true;
                guilds.kick(player, args[1]);
                return true;
            }
            case "leave" -> {
                guilds.leave(player);
                return true;
            }
            default -> guilds.openEntry(player);
        }
        return true;
    }

    private boolean handleWarArenaAdmin(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            return List.of("create", "disband", "invite", "accept", "kick", "leave", "info", "wararena");
        }
        return List.of();
    }
}