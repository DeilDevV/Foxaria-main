package com.foxaria.kits.command;

import com.foxaria.api.model.KitDefinition;
import com.foxaria.api.service.KitService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.kits.gui.KitBrowserMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class KitCommand implements CommandExecutor, TabCompleter {

    private final KitService kitService;
    private final MessageService messages;
    private final MenuManager menuManager;

    public KitCommand(KitService kitService, MessageService messages, MenuManager menuManager) {
        this.kitService = kitService;
        this.messages = messages;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!sender.hasPermission("foxaria.kits.use")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("kits") || args.length == 0) {
            menuManager.open(player, new KitBrowserMenu(kitService, menuManager, player));
            return true;
        }
        kitService.claim(player, args[0].toLowerCase());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("foxaria.kits.use")) {
            return Collections.emptyList();
        }
        return kitService.definitions().stream()
            .map(KitDefinition::id)
            .filter(id -> id.startsWith(args[0].toLowerCase()))
            .collect(Collectors.toList());
    }
}
