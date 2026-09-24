package com.foxaria.security.command;

import com.foxaria.api.service.IntegrationService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.security.JdbcSecurityService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SecurityCommand implements CommandExecutor {

    private final SecurityService securityService;
    private final IntegrationService integrations;
    private final MessageService messages;

    public SecurityCommand(SecurityService securityService, IntegrationService integrations, MessageService messages) {
        this.securityService = securityService;
        this.integrations = integrations;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.security.admin")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Профили безопасности читаются из конфигов. Для полной перезагрузки перезапустите плагин.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("verbose") && sender instanceof Player player && securityService instanceof JdbcSecurityService jdbc) {
            boolean enabled = jdbc.toggleVerbose(player.getUniqueId());
            sender.sendMessage("Режим verbose " + (enabled ? "включён" : "отключён"));
            return true;
        }
        sender.sendMessage("Внешний античит обнаружен: " + (integrations.hasGrim() ? "да" : "нет"));
        return true;
    }
}
