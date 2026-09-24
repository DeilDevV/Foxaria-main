package com.foxaria.api.service;

import org.bukkit.plugin.Plugin;

public interface IntegrationService {

    boolean isEnabled(String pluginName);

    Plugin plugin(String pluginName);

    boolean hasVault();

    boolean hasLuckPerms();

    boolean hasEssentialsX();

    boolean hasGrim();
}
