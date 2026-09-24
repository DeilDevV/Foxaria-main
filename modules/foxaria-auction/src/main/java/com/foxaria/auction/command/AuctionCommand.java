package com.foxaria.auction.command;

import com.foxaria.api.service.AuctionService;
import com.foxaria.api.service.MessageService;
import com.foxaria.auction.gui.AuctionBrowserMenu;
import com.foxaria.auction.gui.AuctionFilter;
import com.foxaria.auction.gui.AuctionMailboxMenu;
import com.foxaria.core.gui.MenuManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.List;

public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final AuctionService auctionService;
    private final MessageService messages;
    private final MenuManager menuManager;

    public AuctionCommand(JavaPlugin plugin, AuctionService auctionService, MessageService messages, MenuManager menuManager) {
        this.plugin = plugin;
        this.auctionService = auctionService;
        this.messages = messages;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!sender.hasPermission("foxaria.auction.use")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length == 0) {
            menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, 0, AuctionFilter.ALL));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            try {
                auctionService.listItem(player, player.getInventory().getItemInMainHand(), new BigDecimal(args[1]));
            } catch (NumberFormatException exception) {
                messages.send(player, "economy.invalid-amount", "&cНекорректная сумма.");
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("expired")) {
            menuManager.open(player, new AuctionMailboxMenu(plugin, auctionService, messages, menuManager, 0));
            return true;
        }
        messages.send(player, "auction.usage", "&cИспользование: /ah, /ah sell <цена>, /ah expired");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.auction.use")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("sell", "expired");
        }
        return List.of();
    }
}
