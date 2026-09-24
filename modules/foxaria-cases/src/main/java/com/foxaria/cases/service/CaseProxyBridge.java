package com.foxaria.cases.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public final class CaseProxyBridge {

    public static final String CHANNEL = "foxaria:proxy";

    private final JavaPlugin plugin;

    public CaseProxyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL)) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        }
    }

    public void unregister() {
        if (plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL)) {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        }
    }

    public boolean sendBungeeReward(Player player, List<String> commands) {
        return send(player, "CaseRewardExec", "bungee", commands);
    }

    public boolean sendServerReward(Player player, String targetServer, List<String> commands) {
        return send(player, "CaseRewardForward", targetServer, commands);
    }

    private boolean send(Player player, String kind, String target, List<String> commands) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(kind);
            out.writeUTF(target);
            out.writeInt(commands.size());
            for (String command : commands) {
                out.writeUTF(command);
            }
            out.close();
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
