package com.foxaria.retention.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.retention.JdbcRetentionService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RetentionCommand implements CommandExecutor {

    private final JdbcRetentionService retentionService;
    private final MessageService messages;

    public RetentionCommand(JdbcRetentionService retentionService, MessageService messages) {
        this.retentionService = retentionService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        String permission = switch (command.getName().toLowerCase()) {
            case "rewards" -> "foxaria.rewards.use";
            case "streak" -> "foxaria.streak.use";
            case "voteclaim" -> "foxaria.voteclaim.use";
            case "refer" -> "foxaria.refer.use";
            case "season" -> "foxaria.season.use";
            default -> "";
        };
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            messages.send(player, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "rewards" -> retentionService.claimPlaytimeRewards(player);
            case "streak" -> retentionService.streak(player).thenAccept(streak ->
                player.sendMessage("Текущая серия входов: " + streak + " дн.")
            );
            case "voteclaim" -> retentionService.claimVoteReward(player, args.length > 0 ? args[0] : "default");
            case "refer" -> {
                if (args.length == 0) {
                    player.sendMessage("Использование: /refer <игрок>");
                    return true;
                }
                OfflinePlayer referrer = Bukkit.getOfflinePlayer(args[0]);
                retentionService.applyReferral(player, referrer);
            }
            case "season" -> player.sendMessage(retentionService.seasonSummary());
            default -> {
            }
        }
        return true;
    }
}
