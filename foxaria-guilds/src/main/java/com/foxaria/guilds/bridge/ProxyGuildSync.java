package com.foxaria.guilds.bridge;

import com.foxaria.guilds.GuildModels.GuildRecord;
import com.foxaria.guilds.GuildService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Синхронизация тега гильдии с Bungee только для отображения ника в табе/списке игроков на прокси.
 */
public final class ProxyGuildSync {

    public static final String CHANNEL = "foxaria:proxy";

    private ProxyGuildSync() {
    }

    public static void registerOutgoing(org.bukkit.plugin.java.JavaPlugin plugin) {
        Messenger messenger = plugin.getServer().getMessenger();
        if (!messenger.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
    }

    public static void send(org.bukkit.plugin.java.JavaPlugin plugin, GuildService guilds, Player player, Optional<GuildRecord> guild) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF("GuildSync");
            out.writeUTF(player.getUniqueId().toString());
            if (guild.isEmpty()) {
                out.writeUTF("");
                out.writeUTF("");
                out.writeUTF("&f");
            } else {
                GuildRecord g = guild.get();
                out.writeUTF(g.id());
                out.writeUTF(g.name());
                String lc = guilds.legacyColorForGuildTag(g.tagColor());
                out.writeUTF(lc == null ? "&f" : lc);
            }
            player.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
        } catch (IOException ignored) {
        }
    }

    public static void sendClear(org.bukkit.plugin.java.JavaPlugin plugin, Player player) {
        send(plugin, null, player, Optional.empty());
    }
}
