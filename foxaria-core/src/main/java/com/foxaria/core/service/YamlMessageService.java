package com.foxaria.core.service;

import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlMessageService implements MessageService {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public YamlMessageService(JavaPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    @Override
    public String raw(String path, String fallback) {
        FileConfiguration messages = configService.module("messages.yml");
        return messages.getString(path, fallback);
    }

    @Override
    public Component component(String path, String fallback, Placeholder... placeholders) {
        return FoxariaText.noItalicDeep(serializer.deserialize(applyPlaceholders(raw(path, fallback), placeholders)));
    }

    @Override
    public void send(CommandSender sender, String path, String fallback, Placeholder... placeholders) {
        Runnable task = () -> sender.sendMessage(component(path, fallback, placeholders));
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private String applyPlaceholders(String input, Placeholder... placeholders) {
        String result = input;
        for (Placeholder placeholder : placeholders) {
            result = result.replace("<" + placeholder.key() + ">", placeholder.value());
        }
        return result;
    }
}
