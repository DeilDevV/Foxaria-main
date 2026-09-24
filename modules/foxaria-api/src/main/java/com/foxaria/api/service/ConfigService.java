package com.foxaria.api.service;

import org.bukkit.configuration.file.FileConfiguration;

public interface ConfigService {

    FileConfiguration main();

    FileConfiguration module(String path);

    void saveDefault(String resourcePath);
}
