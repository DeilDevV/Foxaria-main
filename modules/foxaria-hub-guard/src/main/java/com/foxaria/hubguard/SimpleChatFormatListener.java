package com.foxaria.hubguard;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Лобби/auth без полного Foxaria: префикс из rank-bridge + серый ник.
 */
public final class SimpleChatFormatListener implements Listener {

    /** legacySection ломает &#RRGGBB/§x — в чате «кракозябры»; ampersand-сериализатор Paper понимает & и &#… */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final HubRankBridge ranks;

    public SimpleChatFormatListener(HubRankBridge ranks) {
        this.ranks = ranks;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String prefixFromBridge = ranks.chatPrefix(player);
        String normalized = prefixFromBridge.indexOf('§') >= 0
            ? prefixFromBridge.replace('§', '&')
            : prefixFromBridge;
        final String prefixRaw = (!ranks.hasProxyChatPrefix(player) && ranks.shouldNormalizeMojibakePrefix())
            ? HubEncodingUtil.fixUtf8MisreadAsCp1251(normalized)
            : normalized;
        Component prefixComp = prefixRaw.isEmpty() ? Component.empty() : LEGACY.deserialize(prefixRaw);
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> {
            Component name = Component.text(source.getName(), NamedTextColor.GRAY);
            if (prefixRaw.isEmpty()) {
                return name.append(Component.text(": ", NamedTextColor.DARK_GRAY)).append(message);
            }
            return Component.empty()
                .append(prefixComp)
                .append(Component.text(" "))
                .append(name)
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(message);
        }));
    }
}
