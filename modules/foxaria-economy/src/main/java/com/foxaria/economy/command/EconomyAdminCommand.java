package com.foxaria.economy.command;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;

public final class EconomyAdminCommand implements CommandExecutor {

    private final EconomyService economyService;
    private final MessageService messages;
    private final JavaPlugin plugin;

    public EconomyAdminCommand(EconomyService economyService, MessageService messages, JavaPlugin plugin) {
        this.economyService = economyService;
        this.messages = messages;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.economy.admin")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("token")) {
            return handleToken(sender, args);
        }

        if (args.length < 3) {
            messages.send(sender, "economy.admin-usage", "&c/eco <give|take|set> <игрок> <сумма> &7| &c/eco token <give|take|set> <игрок> <кол-во>");
            return true;
        }

        String action = args[0].toLowerCase();
        if (!action.equals("give") && !action.equals("take") && !action.equals("set")) {
            messages.send(sender, "economy.admin-usage", "&c/eco <give|take|set> <игрок> <сумма> &7| &c/eco token ...");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal amount;
        try {
            amount = new BigDecimal(args[2]).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            messages.send(sender, "economy.invalid-amount", "&cНекорректная сумма.");
            return true;
        }

        CompletableFuture<Void> future = switch (action) {
            case "give" -> economyService.deposit(target.getUniqueId(), amount, "admin_give", null);
            case "take" -> economyService.withdraw(target.getUniqueId(), amount, "admin_take", null);
            case "set" -> economyService.balance(target.getUniqueId())
                .thenCompose(snapshot -> {
                    int compare = snapshot.balance().compareTo(amount);
                    if (compare < 0) {
                        return economyService.deposit(target.getUniqueId(), amount.subtract(snapshot.balance()), "admin_set", null);
                    }
                    if (compare > 0) {
                        return economyService.withdraw(target.getUniqueId(), snapshot.balance().subtract(amount), "admin_set", null);
                    }
                    return CompletableFuture.completedFuture(null);
                });
            default -> CompletableFuture.completedFuture(null);
        };

        future.thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
            messages.send(sender, "economy.admin-updated",
                "&aБаланс игрока <player> обновлён: <action>.",
                new MessageService.Placeholder("player", target.getName() == null ? target.getUniqueId().toString() : target.getName()),
                new MessageService.Placeholder("action", action)
            )));
        return true;
    }

    private boolean handleToken(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "economy.admin-token-usage", "&c/eco token <give|take|set> <игрок> <количество>");
            return true;
        }
        String action = args[1].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            messages.send(sender, "economy.invalid-amount", "&cНекорректное число токенов.");
            return true;
        }
        if (amount < 0) {
            messages.send(sender, "economy.invalid-amount", "&cКоличество не может быть отрицательным.");
            return true;
        }

        if (!action.equals("give") && !action.equals("take") && !action.equals("set")) {
            messages.send(sender, "economy.admin-token-usage", "&c/eco token <give|take|set> <игрок> <количество>");
            return true;
        }

        CompletableFuture<Void> future = switch (action) {
            case "give" -> economyService.depositTokens(target.getUniqueId(), amount, "admin_token_give", null);
            case "take" -> economyService.withdrawTokens(target.getUniqueId(), amount, "admin_token_take", null);
            case "set" -> economyService.balance(target.getUniqueId()).thenCompose(snapshot -> {
                long have = snapshot.tokens();
                if (amount > have) {
                    return economyService.depositTokens(target.getUniqueId(), amount - have, "admin_token_set", null);
                }
                if (amount < have) {
                    return economyService.withdrawTokens(target.getUniqueId(), have - amount, "admin_token_set", null);
                }
                return CompletableFuture.completedFuture(null);
            });
            default -> CompletableFuture.completedFuture(null);
        };

        future.thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
            messages.send(sender, "economy.admin-token-updated",
                "&aТокены игрока <player> обновлены: &f<action> <amount>",
                new MessageService.Placeholder("player", target.getName() == null ? target.getUniqueId().toString() : target.getName()),
                new MessageService.Placeholder("action", action),
                new MessageService.Placeholder("amount", String.valueOf(amount))
            )))
            .exceptionally(ex -> {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(sender, "economy.admin-token-failed", "&cОшибка: <msg>",
                        new MessageService.Placeholder("msg", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage())));
                return null;
            });
        return true;
    }
}
