package com.foxaria.playershops.command;

import com.foxaria.api.model.PlayerShop;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.playershops.JdbcPlayerShopService;
import com.foxaria.playershops.gui.PlayerShopBrowserMenu;
import com.foxaria.playershops.gui.PlayerShopMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;

public final class PlayerShopCommand implements CommandExecutor, TabCompleter {

    private final JdbcPlayerShopService service;
    private final MessageService messages;
    private final MenuManager menuManager;

    public PlayerShopCommand(JdbcPlayerShopService service, MessageService messages, MenuManager menuManager) {
        this.service = service;
        this.messages = messages;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!sender.hasPermission("foxaria.playershops.use")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length == 0) {
            menuManager.open(player, new PlayerShopBrowserMenu(service, false));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "mine" -> service.ensureShop(player).thenAccept(shop ->
                player.getServer().getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () ->
                    menuManager.open(player, new PlayerShopMenu(service, shop, true))
                )
            );
            case "sell" -> {
                if (args.length < 2) {
                    messages.send(player, "playershops.usage", "&cИспользование: /pshop, /pshop mine, /pshop sell <цена>, /pshop inspect <игрок>");
                    return true;
                }
                try {
                    service.listOffer(player, player.getInventory().getItemInMainHand(), new BigDecimal(args[1]));
                } catch (NumberFormatException exception) {
                    messages.send(player, "economy.invalid-amount", "&cНекорректная сумма.");
                }
            }
            case "inspect" -> {
                if (!player.hasPermission("foxaria.playershops.inspect") || args.length < 2) {
                    messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    service.ensureShop(target).thenAccept(shop ->
                        player.getServer().getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () ->
                            menuManager.open(player, new PlayerShopMenu(service, shop, true))
                        )
                    );
                }
            }
            default -> messages.send(player, "playershops.usage", "&cИспользование: /pshop, /pshop mine, /pshop sell <цена>, /pshop inspect <игрок>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.playershops.use")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("mine", "sell", "inspect");
        }
        return List.of();
    }
}
