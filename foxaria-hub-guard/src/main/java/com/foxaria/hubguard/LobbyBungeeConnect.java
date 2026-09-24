package com.foxaria.hubguard;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class LobbyBungeeConnect {

    private LobbyBungeeConnect() {
    }

    public static void register(JavaPlugin plugin) {
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }
    }

    public static void connect(Player player, JavaPlugin plugin, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (IOException ignored) {
        }
    }
}
