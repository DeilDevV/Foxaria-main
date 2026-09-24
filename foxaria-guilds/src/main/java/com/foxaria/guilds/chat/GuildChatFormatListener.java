package com.foxaria.guilds.chat;

import com.foxaria.core.service.FoxariaPermissionService;
import com.foxaria.guilds.GuildModels.GuildRecord;
import com.foxaria.guilds.GuildService;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import java.util.Optional;

/**
 * Чат: префикс + серый ник + название гильдии (без скобок), hover со статистикой.
 */
public final class GuildChatFormatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final FoxariaPermissionService permissions;
    private final GuildService guilds;

    public GuildChatFormatListener(FoxariaPermissionService permissions, GuildService guilds) {
        this.permissions = permissions;
        this.guilds = guilds;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String prefixRaw = permissions.rankPrefixForChat(player);
        Optional<GuildRecord> guild = guilds.guildOf(player.getUniqueId()).join();

        Component prefixComp = prefixRaw.isEmpty()
            ? Component.empty()
            : LEGACY.deserialize(ChatColor.translateAlternateColorCodes('&', prefixRaw));

        final Component guildSuffix;
        if (guild.isPresent()) {
            GuildRecord g = guild.get();
            String colored = ChatColor.translateAlternateColorCodes('&', guilds.legacyColorForGuildTag(g.tagColor()));
            Component tag = LEGACY.deserialize(colored + g.name())
                .hoverEvent(HoverEvent.showText(guilds.guildChatHoverText(g)));
            guildSuffix = Component.text(" ").append(tag);
        } else {
            guildSuffix = Component.empty();
        }

        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> {
            Component name = Component.text(source.getName(), NamedTextColor.GRAY);
            Component line = Component.empty();
            if (!prefixRaw.isEmpty()) {
                line = line.append(prefixComp).append(Component.text(" "));
            }
            return line.append(name).append(guildSuffix).append(Component.text(": ", NamedTextColor.DARK_GRAY)).append(message);
        }));
    }
}
