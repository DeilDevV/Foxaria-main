package com.foxaria.economy.command;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TokenCommand implements CommandExecutor {

    private final EconomyService economyService;
    private final MessageService messages;

    public TokenCommand(EconomyService economyService, MessageService messages) {
        this.economyService = economyService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.economy.token")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        OfflinePlayer target;
        if (args.length > 0) {
            if (sender instanceof Player p && !p.getName().equalsIgnoreCase(args[0]) && !sender.hasPermission("foxaria.economy.token.others")) {
                messages.send(sender, "general.no-permission", "&cНет прав смотреть чужие токены.");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            messages.send(sender, "economy.console-player-required", "&cУкажите игрока.");
            return true;
        }

        economyService.balance(target.getUniqueId()).thenAccept(snapshot -> {
            String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
            messages.send(sender, "economy.tokens-balance", "&bТокены &7игрока &f<player>&7: &b<tokens>",
                new MessageService.Placeholder("player", name),
                new MessageService.Placeholder("tokens", String.valueOf(snapshot.tokens())));
            if (args.length == 0 && sender instanceof Player) {
                messages.send(sender, "economy.tokens-hint", "&7Токены — отдельная валюта. Тратятся в &f/donateshop&7. Монеты: &f/bal");
            }
        });
        return true;
    }
}
