package com.foxaria.api.service;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public interface MessageService {

    String raw(String path, String fallback);

    Component component(String path, String fallback, Placeholder... placeholders);

    void send(CommandSender sender, String path, String fallback, Placeholder... placeholders);

    record Placeholder(String key, String value) {
    }
}
