package com.foxaria.ranks.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.RankService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.ranks.StandaloneRankService;
import com.foxaria.ranks.gui.RankPlayerMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public final class RankCommand implements CommandExecutor, TabCompleter {

    private final RankService rankService;
    private final MessageService messages;
    private final FileConfiguration config;
    private final MenuManager menuManager;

    public RankCommand(RankService rankService, MessageService messages, FileConfiguration config, MenuManager menuManager) {
        this.rankService = rankService;
        this.messages = messages;
        this.config = config;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.ranks.manage")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length == 0 && sender instanceof Player player) {
            menuManager.open(player, new RankPlayerMenu(rankService, config));
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "ranks.usage", "&cИспользование: /rank set <игрок> <группа> или /rank temp <игрок> <группа> <секунды>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", "&cИгрок не найден.");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "set" -> rankService.setPrimaryGroup(target.getUniqueId(), args[2]);
            case "temp" -> {
                long durationSeconds;
                try {
                    durationSeconds = args.length > 3 ? Long.parseLong(args[3]) : 86400L;
                } catch (NumberFormatException exception) {
                    messages.send(sender, "ranks.usage", "&cИспользование: /rank set <игрок> <группа> или /rank temp <игрок> <группа> <секунды>");
                    return true;
                }
                rankService.grantTemporaryGroup(target.getUniqueId(), args[2], durationSeconds);
            }
            default -> messages.send(sender, "ranks.usage", "&cИспользование: /rank set <игрок> <группа> или /rank temp <игрок> <группа> <секунды>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "temp");
        }
        if (args.length == 3) {
            if (rankService instanceof StandaloneRankService standaloneRankService) {
                return standaloneRankService.groups();
            }
            return config.getStringList("groups");
        }
        return List.of();
    }
}
