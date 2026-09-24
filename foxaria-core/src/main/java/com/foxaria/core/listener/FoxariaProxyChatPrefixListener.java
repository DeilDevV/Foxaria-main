package com.foxaria.core.listener;

import com.foxaria.core.service.FoxariaPermissionService;
import com.foxaria.core.service.SidebarService;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Входящие сообщения FoxariaProxy по каналу {@code foxaria:proxy}.
 */
public final class FoxariaProxyChatPrefixListener implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final FoxariaPermissionService permissions;
    private final SidebarService sidebarService;

    public FoxariaProxyChatPrefixListener(
        JavaPlugin plugin,
        FoxariaPermissionService permissions,
        SidebarService sidebarService
    ) {
        this.plugin = plugin;
        this.permissions = permissions;
        this.sidebarService = sidebarService;
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
            handleChatPrefixSync(player, in);
            return;
        }
        if ("CaseRewardExecute".equals(kind)) {
            handleCaseRewardExecute(in);
        }
    }

    private void handleChatPrefixSync(Player player, ByteArrayDataInput in) {
        String prefix;
        try {
            prefix = in.readUTF();
        } catch (Exception ignored) {
            return;
        }
        UUID id = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            permissions.setProxyChatPrefix(id, prefix);
            Player online = plugin.getServer().getPlayer(id);
            if (online != null && online.isOnline()) {
                sidebarService.refresh(online);
            }
        });
    }

    private void handleCaseRewardExecute(ByteArrayDataInput in) {
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (String command : finalCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        });
    }
}
