package com.foxaria.economy.command;

import com.foxaria.api.model.BalanceSnapshot;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;

public final class BalanceCommand implements CommandExecutor {

    private final EconomyService economyService;
    private final MessageService messages;
    private final boolean topMode;
    private final DecimalFormat format = new DecimalFormat("#,##0.00");

    public BalanceCommand(EconomyService economyService, MessageService messages) {
        this(economyService, messages, false);
    }

    public BalanceCommand(EconomyService economyService, MessageService messages, boolean topMode) {
        this.economyService = economyService;
        this.messages = messages;
        this.topMode = topMode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String permission = topMode ? "foxaria.economy.balance.top" : "foxaria.economy.balance";
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (topMode) {
            economyService.top(10).thenAccept(top -> {
                StringBuilder builder = new StringBuilder();
                for (int index = 0; index < top.size(); index++) {
                    BalanceSnapshot snapshot = top.get(index);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(snapshot.playerUuid());
                    builder.append(index + 1)
                        .append(". ")
                        .append(offlinePlayer.getName() == null ? snapshot.playerUuid() : offlinePlayer.getName())
                        .append(" - ")
                        .append(format.format(snapshot.balance()));
                    if (index + 1 < top.size()) {
                        builder.append(", ");
                    }
                }
                messages.send(sender, "economy.baltop", "&eТоп балансов: <entries>", new MessageService.Placeholder("entries", builder.toString()));
            });
            return true;
        }

        OfflinePlayer target;
        if (args.length > 0) {
            if (sender instanceof Player player && !player.getName().equalsIgnoreCase(args[0]) && !sender.hasPermission("foxaria.economy.balance.others")) {
                messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            messages.send(sender, "economy.console-player-required", "&cКонсоль должна указать игрока.");
            return true;
        }

        economyService.balance(target.getUniqueId()).thenAccept(snapshot -> messages.send(
            sender,
            "economy.balance",
            "&eБаланс игрока <player>: <amount>",
            new MessageService.Placeholder("player", target.getName() == null ? target.getUniqueId().toString() : target.getName()),
            new MessageService.Placeholder("amount", format.format(snapshot.balance()))
        ));
        return true;
    }
}
