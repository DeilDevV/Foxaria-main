package com.foxaria.cases.bukkit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

final class FoxariaCasesConfigBootstrap {

    private FoxariaCasesConfigBootstrap() {
    }

    static void ensureDefaults(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create data folder: " + plugin.getDataFolder().getAbsolutePath());
        }
        plugin.saveDefaultConfig();
        saveIfMissing(plugin, "cases.yml");
        saveIfMissing(plugin, "messages.yml");
        importDatabaseFromFoxaria(plugin);
    }

    private static void saveIfMissing(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    /**
     * Если Foxaria уже настроен — копируем database из plugins/Foxaria/config.yml,
     * чтобы не править два конфига вручную.
     */
    private static void importDatabaseFromFoxaria(JavaPlugin plugin) {
        File foxariaConfig = new File(plugin.getDataFolder().getParentFile(), "Foxaria/config.yml");
        if (!foxariaConfig.isFile()) {
            return;
        }
        FileConfiguration casesConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        String password = casesConfig.getString("database.password", "");
        if (password != null && !password.isBlank() && !"change-me".equals(password)) {
            return;
        }
        FileConfiguration foxaria = YamlConfiguration.loadConfiguration(foxariaConfig);
        ConfigurationSection db = foxaria.getConfigurationSection("database");
        if (db == null) {
            return;
        }
        casesConfig.set("database", db.getValues(true));
        try {
            casesConfig.save(new File(plugin.getDataFolder(), "config.yml"));
            plugin.getLogger().info("Database settings imported from plugins/Foxaria/config.yml");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save imported database config", exception);
        }
    }
}
