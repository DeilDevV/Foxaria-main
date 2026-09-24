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
            case "disband" -> {
                guilds.disband(player);
                return true;
            }
            case "create" -> {
                if (args.length < 2) {
                    messages.send(player, "guild.create.usage", "&eИспользование: /guild create <название>");
                    return true;
                }
                if (!player.hasPermission("foxaria.guild.create")) {
                    messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                    return true;
                }
                guilds.create(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                return true;
            }
            case "invite" -> {
                if (args.length < 2) {
                    messages.send(player, "guild.invite.usage", "&eИспользование: /guild invite <игрок>");
                    return true;
                }
                guilds.invite(player, args[1]);
                return true;
            }
            case "accept" -> {
                guilds.acceptInvite(player);
                return true;
            }
            case "decline", "deny" -> {
                guilds.declineInvite(player);
                return true;
            }
            case "kick" -> {
                if (args.length < 2) {
                    messages.send(player, "guild.kick.usage", "&eИспользование: /guild kick <игрок>");
                    return true;
                }
                guilds.kick(player, args[1]);
                return true;
            }
            case "leave" -> {
                guilds.leave(player);
                return true;
            }
            case "promote" -> {
                if (args.length < 2) {
                    messages.send(player, "guild.promote.usage", "&eИспользование: /guild promote <игрок>");
                    return true;
                }
                guilds.promote(player, args[1]);
                return true;
            }
            case "demote" -> {
                if (args.length < 2) {
                    messages.send(player, "guild.demote.usage", "&eИспользование: /guild demote <игрок>");
                    return true;
                }
                guilds.demote(player, args[1]);
                return true;
            }
            default -> guilds.openEntry(player);
        }
        return true;
    }

    private boolean handleWarArenaAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("foxaria.guild.war.admin")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/guild wararena create <id> <display>");
            sender.sendMessage("§e/guild wararena setspawn <id> <a|b>");
            sender.sendMessage("§e/guild wararena enable <id> <true|false>");
            sender.sendMessage("§e/guild wararena list");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c/guild wararena create <id> <display>");
                    return true;
                }
                wars.createArena(args[2], String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                sender.sendMessage("§aАрена создана или обновлена.");
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cТолько игрок может ставить spawn.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§c/guild wararena setspawn <id> <a|b>");
                    return true;
                }
                wars.setArenaSpawn(args[2], args[3].equalsIgnoreCase("a"), player.getLocation());
                sender.sendMessage("§aТочка спавна арены обновлена.");
            }
            case "enable" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c/guild wararena enable <id> <true|false>");
                    return true;
                }
                wars.setArenaEnabled(args[2], Boolean.parseBoolean(args[3]));
                sender.sendMessage("§aСостояние арены обновлено.");
            }
            case "list" -> wars.arenas().thenAccept(list -> {
                sender.sendMessage("§6--- Арены войн ---");
                for (GuildRepository.WarArenaRecord a : list) {
                    sender.sendMessage("§e" + a.arenaId() + " §7(" + a.displayName() + ") §7вкл=" + a.enabled());
                }
            });
            default -> sender.sendMessage("§cНеизвестная подкоманда wararena.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("create", "disband", "invite", "accept", "decline", "kick", "leave", "promote", "demote", "wararena");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("wararena")) {
            return List.of("create", "setspawn", "enable", "list");
        }
        return List.of();
    }
}
