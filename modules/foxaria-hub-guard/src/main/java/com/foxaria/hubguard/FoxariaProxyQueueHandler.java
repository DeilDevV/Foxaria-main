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
 * Ответ FoxariaProxy с размером очереди и синхронизацией wipe-блокировок.
 */
public final class FoxariaProxyQueueHandler implements PluginMessageListener {

    private final FoxariaHubGuardPlugin plugin;
    private final WipeLockRegistry wipeLockRegistry;
    private final Map<UUID, Map<String, Consumer<Integer>>> pending = new ConcurrentHashMap<>();

    public FoxariaProxyQueueHandler(FoxariaHubGuardPlugin plugin, WipeLockRegistry wipeLockRegistry) {
        this.plugin = plugin;
        this.wipeLockRegistry = wipeLockRegistry;
    }

    public void requestWipeLockSync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("WipeLockSyncRequest");
        player.sendPluginMessage(plugin, "foxaria:proxy", out.toByteArray());
    }

    public void request(Player player, String bungeeServerName, Consumer<Integer> callback) {
        if (player == null || bungeeServerName == null || bungeeServerName.isBlank()) {
            return;
        }
        pending.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(bungeeServerName, callback);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("QueueInfo");
        out.writeUTF(bungeeServerName);
        player.sendPluginMessage(plugin, "foxaria:proxy", out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"foxaria:proxy".equals(channel) || player == null || message == null || message.length == 0) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String kind;
        try {
            kind = in.readUTF();
        } catch (Exception ignored) {
            return;
        }
        if ("ChatPrefixSync".equals(kind)) {
            String prefix;
            try {
                prefix = in.readUTF();
            } catch (Exception ignored) {
                return;
            }
            UUID id = player.getUniqueId();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.hubRank().setProxyChatPrefix(id, prefix));
            return;
        }
        if ("WipeLockUpdate".equals(kind)) {
            try {
                String serverName = in.readUTF();
                boolean locked = in.readBoolean();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    wipeLockRegistry.set(serverName, locked));
            } catch (Exception ignored) {
            }
            return;
        }
        if ("WipeLockSyncAll".equals(kind)) {
            try {
                int count = in.readInt();
                java.util.Set<String> lockedServers = new java.util.HashSet<>();
                for (int i = 0; i < count; i++) {
                    lockedServers.add(in.readUTF());
                }
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    wipeLockRegistry.syncAll(lockedServers));
            } catch (Exception ignored) {
            }
            return;
        }
        if (!"QueueInfoReply".equals(kind)) {
            return;
        }
        String server;
        int queueSize;
        try {
            server = in.readUTF();
            queueSize = in.readInt();
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
        int q = queueSize;
        plugin.getServer().getScheduler().runTask(plugin, () -> cb.accept(q));
    }

    public void clearPlayer(Player player) {
        pending.remove(player.getUniqueId());
    }
}
