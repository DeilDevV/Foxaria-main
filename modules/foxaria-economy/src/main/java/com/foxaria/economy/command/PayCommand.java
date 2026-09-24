package com.foxaria.economy.command;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PayCommand implements CommandExecutor {

    private final EconomyService economyService;
    private final MessageService messages;
    private final JavaPlugin plugin;

    public PayCommand(EconomyService economyService, MessageService messages, JavaPlugin plugin) {
        this.economyService = economyService;
        this.messages = messages;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!sender.hasPermission("foxaria.economy.pay")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "economy.pay-usage", "&cИспользование: /pay <игрок> <сумма>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "general.player-not-found", "&cИгрок не найден.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "economy.pay-self", "&cНельзя перевести деньги самому себе.");
            return true;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(args[1]).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            messages.send(player, "economy.invalid-amount", "&cНекорректная сумма.");
            return true;
        }

        BigDecimal feePercent = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.transfer-fee-percent", 2.5D));
        BigDecimal fee = amount.multiply(feePercent).divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
        economyService.transfer(player.getUniqueId(), target.getUniqueId(), amount, fee, "pay")
            .thenRun(() -> {
                messages.send(player, "economy.pay-sent", "&aВы отправили <amount> игроку <player>.", new MessageService.Placeholder("amount", amount.toPlainString()), new MessageService.Placeholder("player", target.getName()));
                messages.send(target, "economy.pay-received", "&aВы получили <amount> от игрока <player>.", new MessageService.Placeholder("amount", amount.toPlainString()), new MessageService.Placeholder("player", player.getName()));
            })
            .exceptionally(throwable -> {
                messages.send(player, "economy.pay-failed", "&cПеревод не выполнен: <error>", new MessageService.Placeholder("error", throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage()));
                return null;
            });
        return true;
    }
}
