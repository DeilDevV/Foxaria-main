package com.foxaria.cases.proxy;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Отдельный прокси-плагин для наград кейсов (target: bungee / другой backend).
 * Не требует обновления FoxariaProxy.jar.
 */
public final class FoxariaCasesProxyPlugin extends Plugin implements Listener {

    public static final String CHANNEL = "foxaria:proxy";

    @Override
    public void onEnable() {
        getProxy().registerChannel(CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("FoxariaCasesProxy enabled on channel " + CHANNEL);
    }

    @Override
    public void onDisable() {
        getProxy().unregisterChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getTag())) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String kind = in.readUTF();
            if ("CaseRewardExec".equals(kind)) {
                event.setCancelled(true);
                in.readUTF();
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    getProxy().getPluginManager().dispatchCommand(getProxy().getConsole(), in.readUTF());
                }
                return;
            }
            if ("CaseRewardForward".equals(kind)) {
                event.setCancelled(true);
                String targetServer = in.readUTF();
                int count = in.readInt();
                List<String> commands = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    commands.add(in.readUTF());
                }
                forwardToBackend(targetServer, commands);
            }
        } catch (IOException ignored) {
        }
    }

    private void forwardToBackend(String targetServer, List<String> commands) {
        ServerInfo server = getProxy().getServerInfo(targetServer);
        if (server == null || commands.isEmpty()) {
            getLogger().warning("Case reward forward: server not found " + targetServer);
            return;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("CaseRewardExecute");
            out.writeUTF("");
            out.writeUTF("");
            out.writeInt(commands.size());
            for (String command : commands) {
                out.writeUTF(command);
            }
            out.close();
            byte[] data = baos.toByteArray();
            for (ProxiedPlayer player : server.getPlayers()) {
                if (player.getServer() != null) {
                    player.getServer().sendData(CHANNEL, data);
                    return;
                }
            }
            getLogger().warning("Case reward forward failed: no players on " + targetServer);
        } catch (IOException ignored) {
        }
    }
}
