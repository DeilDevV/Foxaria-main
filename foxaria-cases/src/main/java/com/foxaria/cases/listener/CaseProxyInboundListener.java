package com.foxaria.cases.listener;

import com.foxaria.cases.service.CaseProxyBridge;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Принимает с BungeeCord команды наград ({@code CaseRewardExecute}).
 * Нужен отдельному плагину FoxariaCases, если Foxaria.jar на сервере старый.
 */
public final class CaseProxyInboundListener implements PluginMessageListener {

    private final JavaPlugin plugin;

    public CaseProxyInboundListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CaseProxyBridge.CHANNEL.equals(channel) || message == null || message.length == 0) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String kind;
        try {
            kind = in.readUTF();
        } catch (Exception ignored) {
            return;
        }
        if (!"CaseRewardExecute".equals(kind)) {
            return;
        }
        int count;
        try {
            in.readUTF();
            in.readUTF();
            count = in.readInt();
        } catch (Exception ignored) {
            return;
        }
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                commands.add(in.readUTF());
            } catch (Exception ignored) {
                return;
            }
        }
        List<String> finalCommands = List.copyOf(commands);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String command : finalCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        });
    }
}
