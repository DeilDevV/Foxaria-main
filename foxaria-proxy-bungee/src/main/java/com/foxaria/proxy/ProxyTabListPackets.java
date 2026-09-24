package com.foxaria.proxy;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.protocol.Protocol;
import net.md_5.bungee.protocol.ProtocolConstants;
import net.md_5.bungee.protocol.packet.PlayerListItem;
import net.md_5.bungee.protocol.packet.PlayerListItemUpdate;

import java.util.EnumSet;

/**
 * Таб на прокси: {@link ProxiedPlayer#setDisplayName(String)} не влияет на клиент.
 * <p>С 1.19.3 в GAME фазе вместо {@link PlayerListItem} используется {@link PlayerListItemUpdate}
 * (см. {@code Protocol.TO_CLIENT} в BungeeCord — у PlayerListItem id {@code -1} для 1.19.3+).
 */
public final class ProxyTabListPackets {

    private ProxyTabListPackets() {
    }

    /**
     * @param legacyUseAndColors строка с & и/или &#RRGGBB — будет прогнана через {@link ProxyColorUtil#colorize}
     */
    public static void broadcastPlayerListDisplayName(ProxiedPlayer subject, String legacyUseAndColors) {
        String legacy = ProxyColorUtil.colorize(legacyUseAndColors);
        BaseComponent[] parts = TextComponent.fromLegacyText(legacy);
        BaseComponent display;
        if (parts.length == 0) {
            display = new TextComponent("");
        } else if (parts.length == 1) {
            display = parts[0];
        } else {
            TextComponent root = new TextComponent("");
            for (BaseComponent p : parts) {
                root.addExtra(p);
            }
            display = root;
        }

        for (ProxiedPlayer viewer : ProxyServer.getInstance().getPlayers()) {
            if (viewer.getServer() == null || !viewer.isConnected()) {
                continue;
            }
            if (!(viewer instanceof UserConnection uc)) {
                continue;
            }
            // LOGIN / CONFIGURATION: PlayerList* недопустимы (EncoderException в UpstreamBridge)
            if (uc.getCh().getEncodeProtocol() != Protocol.GAME) {
                continue;
            }
            int ver = viewer.getPendingConnection().getVersion();
            if (ver >= ProtocolConstants.MINECRAFT_1_19_3) {
                PlayerListItemUpdate upd = new PlayerListItemUpdate();
                upd.setActions(EnumSet.of(PlayerListItemUpdate.Action.UPDATE_DISPLAY_NAME));
                PlayerListItem.Item item = new PlayerListItem.Item();
                item.setUuid(subject.getUniqueId());
                item.setDisplayName(display);
                upd.setItems(new PlayerListItem.Item[]{item});
                viewer.unsafe().sendPacket(upd);
            } else {
                PlayerListItem packet = new PlayerListItem();
                packet.setAction(PlayerListItem.Action.UPDATE_DISPLAY_NAME);
                PlayerListItem.Item item = new PlayerListItem.Item();
                item.setUuid(subject.getUniqueId());
                item.setDisplayName(display);
                packet.setItems(new PlayerListItem.Item[]{item});
                viewer.unsafe().sendPacket(packet);
            }
        }
    }
}
