package com.foxaria.admin.command;

import com.foxaria.admin.AdminState;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import com.foxaria.core.command.StaffCommandInput;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MaintenanceCommand implements CommandExecutor {

    private final AdminState state;
    private final MessageService messages;

    public MaintenanceCommand(AdminState state, MessageService messages) {
        this.state = state;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] rawArgs) {
        if (!FoxariaStaffPermissions.has(sender, "foxaria.admin.maintenance")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        StaffCommandInput input = StaffCommandInput.parse(rawArgs);
        String[] args = input.args();
        boolean silent = input.silent();
        boolean enabled = args.length > 0 && args[0].equalsIgnoreCase("on");
        state.maintenance(enabled);

        Component message = Component.text("Режим техработ " + (enabled ? "включён" : "отключён"));
        if (silent) {
            sender.sendMessage(message);
            Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("foxaria.admin.maintenance") && !player.equals(sender))
                .forEach(player -> player.sendMessage(Component.text("[Тихо] ").append(message)));
        } else {
            Bukkit.broadcast(message);
        }

        messages.send(sender, "admin.maintenance", "&eРежим техработ обновлён.");
        return true;
    }
}
