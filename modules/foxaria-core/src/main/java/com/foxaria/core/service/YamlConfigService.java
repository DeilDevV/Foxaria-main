package com.foxaria.core.service;

import com.foxaria.api.service.ConfigService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class YamlConfigService implements ConfigService {

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> cache = new ConcurrentHashMap<>();

    public YamlConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public FileConfiguration main() {
        return module("config.yml");
    }

    @Override
    public FileConfiguration module(String path) {
        return cache.computeIfAbsent(path, this::load);
    }

    @Override
    public void saveDefault(String resourcePath) {
        File destination = new File(plugin.getDataFolder(), resourcePath);
        if (destination.exists()) {
            return;
        }
        File parent = destination.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        plugin.saveResource(resourcePath, false);
    }

    private FileConfiguration load(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            saveDefault(path);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
