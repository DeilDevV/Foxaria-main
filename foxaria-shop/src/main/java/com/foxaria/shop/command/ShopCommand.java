package com.foxaria.shop.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopCommand implements CommandExecutor {

    private final ShopService shopService;
    private final MessageService messages;

    public ShopCommand(ShopService shopService, MessageService messages) {
        this.shopService = shopService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!sender.hasPermission("foxaria.shop.use")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        shopService.open(player);
        return true;
    }
}
