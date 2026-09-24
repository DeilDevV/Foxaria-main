package com.foxaria.core.service;

import com.foxaria.api.service.IntegrationService;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitIntegrationService implements IntegrationService {

    private final PluginManager pluginManager;

    public BukkitIntegrationService(JavaPlugin plugin) {
        this.pluginManager = plugin.getServer().getPluginManager();
    }

    @Override
    public boolean isEnabled(String pluginName) {
        return pluginManager.isPluginEnabled(pluginName);
    }

    @Override
    public Plugin plugin(String pluginName) {
        return pluginManager.getPlugin(pluginName);
    }

    @Override
    public boolean hasVault() {
        return isEnabled("Vault");
    }

    @Override
    public boolean hasLuckPerms() {
        return isEnabled("LuckPerms");
    }

    @Override
    public boolean hasEssentialsX() {
        return isEnabled("Essentials");
    }

    @Override
    public boolean hasGrim() {
        return isEnabled("GrimAC") || isEnabled("Grim");
    }
}
