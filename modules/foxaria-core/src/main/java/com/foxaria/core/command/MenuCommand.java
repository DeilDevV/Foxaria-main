package com.foxaria.core.command;

import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.gui.PlayerMainMenu;
import com.foxaria.core.service.PlayerFlowService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MenuManager menuManager;
    private final ServiceRegistry services;
    private final ConfigService configs;
    private final MessageService messages;

    public MenuCommand(JavaPlugin plugin, MenuManager menuManager, ServiceRegistry services, ConfigService configs, MessageService messages) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.services = services;
        this.configs = configs;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }

        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        if (playerFlowService != null) {
            if (playerFlowService.isAuthStage(player)) {
                messages.send(player, "flow.must-auth-first", "&cСначала завершите авторизацию через /register или /login.");
                return true;
            }
            if (playerFlowService.isSelectorStage(player)) {
                if (playerFlowService.isServerSelectorEnabled()) {
                    playerFlowService.openSelectorMenu(player);
                } else {
                    menuManager.open(player, new PlayerMainMenu(plugin, services, configs));
                }
                return true;
            }
        }

        menuManager.open(player, new PlayerMainMenu(plugin, services, configs));
        return true;
    }
}
