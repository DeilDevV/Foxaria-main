package com.foxaria.moderation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Сетевые объявления через FoxariaProxy (Bungee) по каналу foxaria:proxy.
 * Отправка возможна только через игрока (требование plugin messaging), поэтому если онлайн 0 — возвращаем false.
 */
public final class ProxyNetworkAnnounce {

    public static final String CHANNEL = "foxaria:proxy";

    private ProxyNetworkAnnounce() {
    }

    public static void registerOutgoing(JavaPlugin plugin) {
        Messenger messenger = plugin.getServer().getMessenger();
        if (!messenger.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
    }

    /**
     * @return true если отправка в прокси выполнена
     */
    public static boolean tryBroadcast(JavaPlugin plugin, String mainLegacy, String hoverLegacyMultiline) {
        if (plugin == null) {
            return false;
        }
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return false;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF("NetAnnounce");
            out.writeUTF(mainLegacy == null ? "" : mainLegacy);
            out.writeUTF(hoverLegacyMultiline == null ? "" : hoverLegacyMultiline);
            out.close();
            carrier.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}

