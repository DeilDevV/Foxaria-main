package com.foxaria.core.service;

import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.text.FoxariaColors;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlMessageService implements MessageService {

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private static final String DEFAULT_PREFIX =
        "<gradient:#FF6A00:#FFB347>\u2588 FOXARIA</gradient> &8\u00bb &r";

    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    // FoxariaColors отдаёт уже раскрашенный текст с §-кодами (включая §x-hex),
    // поэтому его разбираем сериализатором секции, а не амперсанда.
    private final LegacyComponentSerializer sectionSerializer = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexCharacter('x')
        .hexColors()
        .build();

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
        return FoxariaText.noItalicDeep(sectionSerializer.deserialize(
            FoxariaColors.colorize(applyPlaceholders(raw(path, fallback), placeholders))));
    }

    /**
     * Системное сообщение с единым префиксом Foxaria.
     * Префикс добавляется только при отправке в чат — в GUI ({@link #component})
     * он не нужен, иначе поедет вёрстка меню.
     */
    public Component chatComponent(String path, String fallback, Placeholder... placeholders) {
        String body = applyPlaceholders(raw(path, fallback), placeholders);
        if (body == null || body.isBlank()) {
            return Component.empty();
        }
        return FoxariaText.noItalicDeep(sectionSerializer.deserialize(FoxariaColors.colorize(withPrefix(body))));
    }

    /** Префикс из messages.yml (или дефолтный градиент), уже раскрашенный. */
    public String prefix() {
        FileConfiguration messages = configService.module("messages.yml");
        if (!messages.getBoolean("prefix.enabled", true)) {
            return "";
        }
        return messages.getString("prefix.text", DEFAULT_PREFIX);
    }

    private String withPrefix(String body) {
        String prefix = prefix();
        if (prefix == null || prefix.isBlank()) {
            return body;
        }
        // Многострочные сообщения: префикс только на первой строке.
        return prefix + body;
    }

    @Override
    public void send(CommandSender sender, String path, String fallback, Placeholder... placeholders) {
        Runnable task = () -> sender.sendMessage(chatComponent(path, fallback, placeholders));
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
