package com.foxaria.shop.progression;

import com.foxaria.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class QuestCommand implements CommandExecutor {

    private final ProgressionService progression;
    private final MessageService messages;

    public QuestCommand(ProgressionService progression, MessageService messages) {
        this.progression = progression;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cТолько игроки.");
            return true;
        }
        if (!player.hasPermission("foxaria.quest.use")) {
            messages.send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        progression.openQuestMenu(player);
        return true;
    }
}
