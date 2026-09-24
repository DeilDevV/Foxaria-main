package com.foxaria.store;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TebexQueuePoller {

    private final JavaPlugin plugin;
    private final TebexApiClient apiClient;
    private final AuditService audits;
    private volatile boolean scheduled;

    public TebexQueuePoller(JavaPlugin plugin, TebexApiClient apiClient, AuditService audits) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.audits = audits;
    }

    public void start(long initialDelayTicks) {
        if (scheduled) {
            return;
        }
        scheduled = true;
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::poll, initialDelayTicks);
    }

    private void poll() {
        if (!plugin.isEnabled()) {
            return;
        }
        long nextDelay = 20L * 90L;
        try {
            TebexApiClient.DuePlayersResponse due = apiClient.duePlayers();
            nextDelay = 20L * Math.max(15, due.meta().nextCheck());
            if (due.meta().executeOffline()) {
                executeCommands(apiClient.offlineCommands().commands());
            }
            for (TebexApiClient.DuePlayer player : due.players()) {
                Player online = resolveOnline(player.uuid(), player.name());
                if (online == null) {
                    continue;
                }
                executeCommands(apiClient.onlineCommands(player.id()).commands());
            }
        } catch (Exception exception) {
            audits.append(new AuditEvent(
                "TEBEX_QUEUE_POLL_FAILED",
                null,
                null,
                "system",
                null,
                "Tebex queue poll failed",
                java.util.Map.of("error", exception.getMessage()),
                System.currentTimeMillis()
            ));
        } finally {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::poll, nextDelay);
        }
    }

    private void executeCommands(List<TebexApiClient.QueuedCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (TebexApiClient.QueuedCommand command : commands) {
            if (command.conditions() != null && command.conditions().slots() > 0 && command.player() != null) {
                Player target = resolveOnline(command.player().uuid(), command.player().name());
                if (target == null || freeSlots(target) < command.conditions().slots()) {
                    continue;
                }
            }
            long delayTicks = command.conditions() == null ? 0L : command.conditions().delay() * 20L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), replacePlaceholders(command));
                audits.append(new AuditEvent(
                    "TEBEX_QUEUE_COMMAND_EXECUTED",
                    command.player() == null ? null : parseUuid(command.player().uuid()),
                    command.player() == null ? null : parseUuid(command.player().uuid()),
                    "tebex",
                    command.player() == null ? null : command.player().name(),
                    "Executed Tebex queue command",
                    java.util.Map.of("commandId", String.valueOf(command.id()), "payment", String.valueOf(command.payment())),
                    System.currentTimeMillis()
                ));
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        apiClient.deleteCommands(List.of(command.id()));
                    } catch (Exception exception) {
                        audits.append(new AuditEvent(
                            "TEBEX_QUEUE_DELETE_FAILED",
                            null,
                            null,
                            "system",
                            null,
                            "Failed to ack Tebex command",
                            java.util.Map.of("error", exception.getMessage(), "commandId", String.valueOf(command.id())),
                            System.currentTimeMillis()
                        ));
                    }
                });
            }, delayTicks);
        }
    }

    private String replacePlaceholders(TebexApiClient.QueuedCommand command) {
        String result = command.command();
        if (command.player() != null) {
            result = result.replace("{name}", command.player().name());
            result = result.replace("{id}", command.player().uuid());
        }
        return result;
    }

    private Player resolveOnline(String uuidRaw, String name) {
        UUID uuid = parseUuid(uuidRaw);
        if (uuid != null) {
            Player byUuid = Bukkit.getPlayer(uuid);
            if (byUuid != null) {
                return byUuid;
            }
        }
        return name == null ? null : Bukkit.getPlayerExact(name);
    }

    private int freeSlots(Player player) {
        int free = 0;
        for (var item : player.getInventory().getStorageContents()) {
            if (item == null) {
                free++;
            }
        }
        return free;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace("-", "");
        if (normalized.length() == 32) {
            normalized = normalized.substring(0, 8) + "-" + normalized.substring(8, 12) + "-" + normalized.substring(12, 16) + "-" + normalized.substring(16, 20) + "-" + normalized.substring(20);
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
