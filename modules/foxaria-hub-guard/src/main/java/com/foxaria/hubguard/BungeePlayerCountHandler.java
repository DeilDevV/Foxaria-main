package com.foxaria.hubguard;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Запрос онлайна на другом сервере сети через канал BungeeCord / PlayerCount.
 */
public final class BungeePlayerCountHandler implements PluginMessageListener {

    private final FoxariaHubGuardPlugin plugin;
    /** Игрок → (имя сервера Bungee → callback). */
    private final Map<UUID, Map<String, Consumer<Integer>>> pending = new ConcurrentHashMap<>();

    public BungeePlayerCountHandler(FoxariaHubGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String bungeeServerName, Consumer<Integer> callback) {
        if (player == null || bungeeServerName == null || bungeeServerName.isBlank()) {
            return;
        }
        pending.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(bungeeServerName, callback);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerCount");
        out.writeUTF(bungeeServerName);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"BungeeCord".equals(channel) || player == null || message == null || message.length == 0) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String sub;
        try {
            sub = in.readUTF();
        } catch (Exception ignored) {
            return;
        }
        if (!"PlayerCount".equals(sub)) {
            return;
        }
        String server;
        int count;
        try {
            server = in.readUTF();
            count = in.readInt();
        } catch (Exception ignored) {
            return;
        }
        Map<String, Consumer<Integer>> map = pending.get(player.getUniqueId());
        if (map == null) {
            return;
        }
        Consumer<Integer> cb = map.remove(server);
        if (map.isEmpty()) {
            pending.remove(player.getUniqueId());
        }
        if (cb == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> cb.accept(count));
    }

    public void clearPlayer(Player player) {
        pending.remove(player.getUniqueId());
    }
}
