package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.service.PlayerFlowService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ServerSelectorCommand implements CommandExecutor {

    private final PlayerFlowService playerFlowService;
    private final MessageService messages;

    public ServerSelectorCommand(PlayerFlowService playerFlowService, MessageService messages) {
        this.playerFlowService = playerFlowService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        playerFlowService.openOrRouteSelector(player);
        return true;
    }
}
