package com.foxaria.core.listener;

import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.api.service.GuildProfileService;
import com.foxaria.core.service.FoxariaPermissionService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * Единый чат Foxaria.
 * ! = Глобал, # = Стафф, без префикса = Локал (50 блоков).
 *
 * Тег гильдии берётся через GuildProfileService (API модуль, доступен из core).
 * Цвет тега пока берётся из guildTagOf() — если нужен прокачиваемый цвет,
 * его можно добавить в GuildProfileService.guildTagOf() на стороне guilds-модуля.
 */
public final class InteractiveChatListener implements Listener {

    private static final TextColor GLOBAL_COLOR = TextColor.color(0xFFAA55);
    private static final TextColor LOCAL_COLOR = TextColor.color(0x55CCFF);
    private static final TextColor STAFF_COLOR = TextColor.color(0xFF5555);
    private static final TextColor FOXARIA_COLOR = TextColor.color(0xFFAA55);
    private static final TextColor ARROW_COLOR = TextColor.color(0x555555);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final ServiceRegistry services;

    public InteractiveChatListener(JavaPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());

        ChatType type;
        String message;
        if (rawMessage.startsWith("#")) {
            if (!player.hasPermission("foxaria.staff.chat")) {
                player.sendMessage(Component.text("У вас нет доступа к стафф-чату.", NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }
            type = ChatType.STAFF;
            message = rawMessage.substring(1).trim();
        } else if (rawMessage.startsWith("!")) {
            type = ChatType.GLOBAL;
            message = rawMessage.substring(1).trim();
        } else {
            type = ChatType.LOCAL;
            message = rawMessage.trim();
        }

        if (message.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        event.viewers().clear();

        // Ранг
        FoxariaPermissionService perms = services.optional(FoxariaPermissionService.class);
        String rankPrefix = perms != null ? perms.rankPrefixForChat(player) : "";
        Component rankComp = rankPrefix.isEmpty()
            ? Component.empty()
            : LEGACY.deserialize(rankPrefix).append(Component.text(" "));

        // Тег гильдии (через GuildProfileService из API — доступен из core)
        Component guildComp = Component.empty();
        GuildProfileService guildProfile = services.optional(GuildProfileService.class);
        if (guildProfile != null) {
            try {
                Optional<String> nameOpt = guildProfile.guildNameOf(player.getUniqueId()).join();
                if (nameOpt.isPresent()) {
                    guildComp = Component.text(" [" + nameOpt.get() + "]", TextColor.color(0x55FF55))
                        .hoverEvent(HoverEvent.showText(
                            Component.text("Гильдия: " + nameOpt.get(), NamedTextColor.GREEN)
                                .append(Component.newline())
                                .append(Component.text("Нажми для инфо", NamedTextColor.DARK_GRAY))
                        ))
                        .clickEvent(ClickEvent.runCommand("/g"));
                }
            } catch (Exception ignored) {}
        }

        // Сборка
        Component foxariaPrefix = Component.text("FOXARIA ", FOXARIA_COLOR, TextDecoration.BOLD);
        Component typeMarker = switch (type) {
            case GLOBAL -> Component.text("<G> ", GLOBAL_COLOR, TextDecoration.BOLD);
            case LOCAL -> Component.text("<L> ", LOCAL_COLOR, TextDecoration.BOLD);
            case STAFF -> Component.text("<S> ", STAFF_COLOR, TextDecoration.BOLD);
        };

        Component playerComp = Component.text(player.getName(), NamedTextColor.WHITE)
            .hoverEvent(HoverEvent.showText(
                Component.text("Написать ЛС → ", NamedTextColor.GRAY)
                    .append(Component.text(player.getName(), NamedTextColor.GOLD))
            ))
            .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));

        Component arrow = Component.text(" » ", ARROW_COLOR, TextDecoration.BOLD);
        NamedTextColor msgColor = type == ChatType.STAFF ? NamedTextColor.YELLOW : NamedTextColor.WHITE;

        Component finalMessage = Component.text()
            .append(foxariaPrefix)
            .append(typeMarker)
            .append(rankComp)
            .append(playerComp)
            .append(guildComp)
            .append(arrow)
            .append(Component.text(message, msgColor))
            .build();

        switch (type) {
            case GLOBAL -> Bukkit.broadcast(finalMessage);
            case LOCAL -> {
                double r2 = 50.0 * 50.0;
                for (Player v : Bukkit.getOnlinePlayers()) {
                    if (v.getWorld().equals(player.getWorld())
                        && v.getLocation().distanceSquared(player.getLocation()) <= r2) {
                        v.sendMessage(finalMessage);
                    }
                }
                Bukkit.getConsoleSender().sendMessage(finalMessage);
            }
            case STAFF -> {
                for (Player v : Bukkit.getOnlinePlayers()) {
                    if (v.hasPermission("foxaria.staff.chat")) {
                        v.sendMessage(finalMessage);
                    }
                }
                Bukkit.getConsoleSender().sendMessage(finalMessage);
            }
        }
    }

    private enum ChatType { GLOBAL, LOCAL, STAFF }
}
