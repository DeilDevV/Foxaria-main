package com.foxaria.core.command;

import com.foxaria.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Игровой сервер за Bungee: выбор мира только из лобби (компас), не через /server здесь.
 */
public final class ServerDisabledCommand implements CommandExecutor {

    private final MessageService messages;

    public ServerDisabledCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        messages.send(sender, "flow.server-disabled-backend", "&cВыбор сервера доступен только в лобби (меню с компасом).");
        return true;
    }
}
