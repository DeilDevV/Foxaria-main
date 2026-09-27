package com.foxaria.guilds.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.guilds.GuildRepository;
import com.foxaria.guilds.GuildService;
import com.foxaria.guilds.GuildWarEngine;
import com.foxaria.guilds.chat.GuildChatFormatListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class GuildCommand implements CommandExecutor, TabCompleter {
    private final GuildService guilds;
    private final GuildWarEngine wars;
    private final MessageService messages;
    private final GuildChatFormatListener guildChat;

    private static final Set<String> SUBCOMMANDS = Set.of(
        "create", "disband", "invite", "accept", "decline", "deny",
        "kick", "leave", "promote", "demote", "wararena"
    );

    public GuildCommand(GuildService guilds, GuildWarEngine wars, MessageService messages, GuildChatFormatListener guildChat) {
        this.guilds = guilds; this.wars = wars; this.messages = messages; this.guildChat = guildChat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("wararena")) return handleWarArenaAdmin(sender, args);

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!player.hasPermission("foxaria.guild.use")) { messages.send(player, "general.no-permission", "&cУ вас нет прав."); return true; }
        if (args.length == 0) { guilds.openEntry(player); return true; }

        // /g текст = чат гильдии (только если первое слово НЕ подкоманда)
        if (label.equalsIgnoreCase("g") && guildChat != null && !SUBCOMMANDS.contains(args[0].toLowerCase())) {
            guildChat.sendGuildMessage(player, String.join(" ", args));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "disband" -> { guilds.disband(player); return true; }
            case "create" -> {
                if (args.length < 2) { messages.send(player, "guild.create-usage", "&cИспользование: /guild create <название>"); return true; }
                if (!player.hasPermission("foxaria.guild.create")) { messages.send(player, "general.no-permission", "&cУ вас нет прав."); return true; }
                guilds.create(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length))); return true;
            }
            case "invite" -> {
                if (args.length < 2) { messages.send(player, "guild.invite-usage", "&cИспользование: /guild invite <ник>"); return true; }
                guilds.invite(player, args[1]); return true;
            }
            case "accept" -> { guilds.acceptInvite(player); return true; }
            case "decline", "deny" -> { guilds.declineInvite(player); return true; }
            case "kick" -> {
                if (args.length < 2) { messages.send(player, "guild.kick-usage", "&cИспользование: /guild kick <ник>"); return true; }
                guilds.kick(player, args[1]); return true;
            }
            case "leave" -> { guilds.leave(player); return true; }
            case "promote" -> {
                if (args.length < 2) { messages.send(player, "guild.promote-usage", "&cИспользование: /guild promote <ник>"); return true; }
                guilds.promote(player, args[1]); return true;
            }
            case "demote" -> {
                if (args.length < 2) { messages.send(player, "guild.demote-usage", "&cИспользование: /guild demote <ник>"); return true; }
                guilds.demote(player, args[1]); return true;
            }
            default -> guilds.openEntry(player);
        }
        return true;
    }

    private boolean handleWarArenaAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("foxaria.guild.war.admin")) { messages.send(sender, "general.no-permission", "&cУ вас нет прав."); return true; }
        if (args.length < 2) {
            sender.sendMessage("§e/guild wararena create <id> <displayName>");
            sender.sendMessage("§e/guild wararena setspawn <id> <a|b>");
            sender.sendMessage("§e/guild wararena enable <id> <true|false>");
            sender.sendMessage("§e/guild wararena list");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> { if (args.length < 4) { sender.sendMessage("§cИспользование: /guild wararena create <id> <displayName>"); return true; } wars.createArena(args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length))); sender.sendMessage("§aАрена создана."); }
            case "setspawn" -> { if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько игрок."); return true; } if (args.length < 4) { sender.sendMessage("§cИспользование: setspawn <id> <a|b>"); return true; } wars.setArenaSpawn(args[2], args[3].equalsIgnoreCase("a"), player.getLocation()); sender.sendMessage("§aSpawn обновлён."); }
            case "enable" -> { if (args.length < 4) { sender.sendMessage("§cИспользование: enable <id> <true|false>"); return true; } wars.setArenaEnabled(args[2], Boolean.parseBoolean(args[3])); sender.sendMessage("§aСостояние обновлено."); }
            case "list" -> wars.arenas().thenAccept(list -> { sender.sendMessage("§6--- Арены войн ---"); for (GuildRepository.WarArenaRecord a : list) sender.sendMessage("§e" + a.arenaId() + " §7(" + a.displayName() + ") §7вкл=" + a.enabled()); });
            default -> sender.sendMessage("§cНеизвестная подкоманда wararena.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) return List.of("create", "disband", "invite", "accept", "decline", "kick", "leave", "promote", "demote", "wararena");
        if (args.length == 2 && args[0].equalsIgnoreCase("wararena")) return List.of("create", "setspawn", "enable", "list");
        return List.of();
    }
}
