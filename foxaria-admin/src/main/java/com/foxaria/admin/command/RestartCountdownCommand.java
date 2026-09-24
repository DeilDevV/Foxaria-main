package com.foxaria.admin.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import com.foxaria.core.command.StaffCommandInput;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RestartCountdownCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MessageService messages;

    public RestartCountdownCommand(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] rawArgs) {
        if (!FoxariaStaffPermissions.has(sender, "foxaria.admin.restart")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        StaffCommandInput input = StaffCommandInput.parse(rawArgs);
        String[] args = input.args();
        boolean silent = input.silent();

        int seconds;
        try {
            seconds = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        } catch (NumberFormatException exception) {
            messages.send(sender, "economy.invalid-amount", "&cНекорректное число.");
            return true;
        }

        for (int remaining = seconds; remaining >= 0; remaining -= 10) {
            int current = remaining;
            long tickDelay = (seconds - current) * 20L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Component message = Component.text("Рестарт сервера через " + current + "с");
                if (silent) {
                    sender.sendMessage(Component.text("[Тихо] ").append(message));
                    Bukkit.getOnlinePlayers().stream()
                        .filter(player -> player.hasPermission("foxaria.admin.restart") && !player.equals(sender))
                        .forEach(player -> player.sendMessage(Component.text("[Тихо] ").append(message)));
                    return;
                }
                Bukkit.broadcast(message);
            }, tickDelay);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, seconds * 20L);
        messages.send(sender, "admin.restart", "&cТаймер рестарта запущен.");
        return true;
    }
}
