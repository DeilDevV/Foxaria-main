package com.foxaria.cases.service;

import com.foxaria.cases.CaseRepository;
import com.foxaria.cases.model.CaseReward;
import com.foxaria.cases.model.RewardCommandGroup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CaseRewardExecutor {

    private final JavaPlugin plugin;
    private final ConfigCaseService config;
    private final CaseRepository repository;
    private final CaseProxyBridge proxyBridge;

    public CaseRewardExecutor(
        JavaPlugin plugin,
        ConfigCaseService config,
        CaseRepository repository,
        CaseProxyBridge proxyBridge
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.proxyBridge = proxyBridge;
    }

    public void execute(Player player, String caseId, CaseReward reward) {
        for (RewardCommandGroup group : reward.commandGroups()) {
            dispatch(player, caseId, reward.id(), group);
        }
    }

    public void executePending(UUID playerUuid, String playerName, String caseId, String rewardId, List<String> commands) {
        runLocalCommands(playerName, playerUuid, caseId, rewardId, commands);
    }

    private void dispatch(Player player, String caseId, String rewardId, RewardCommandGroup group) {
        String target = normalizeTarget(group.target());
        List<String> resolved = resolveCommands(group.commands(), player, caseId, rewardId);
        if (resolved.isEmpty()) {
            return;
        }
        if ("bungee".equals(target)) {
            boolean sent = proxyBridge.sendBungeeReward(player, resolved);
            if (!sent) {
                storePending(player.getUniqueId(), "bungee", resolved);
            }
            return;
        }
        if (target.equalsIgnoreCase(config.serverId())) {
            runLocalCommands(player.getName(), player.getUniqueId(), caseId, rewardId, resolved);
            return;
        }
        boolean sent = proxyBridge.sendServerReward(player, target, resolved);
        if (!sent) {
            storePending(player.getUniqueId(), target, resolved);
        }
    }

    private void runLocalCommands(String playerName, UUID playerUuid, String caseId, String rewardId, List<String> commands) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String command : commands) {
                String resolved = applyPlaceholders(command, playerName, playerUuid, caseId, rewardId);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            }
        });
    }

    public void flushPending(Player player) {
        repository.pendingRewards(player.getUniqueId(), config.serverId()).whenComplete((records, throwable) -> {
            if (throwable != null || records == null || records.isEmpty()) {
                return;
            }
            for (CaseRepository.PendingRewardRecord record : records) {
                List<String> commands = decodeCommands(record.commandsJson());
                runLocalCommands(player.getName(), player.getUniqueId(), "pending", "pending", commands);
                repository.removePendingReward(record.id());
            }
        });
    }

    private void storePending(UUID playerUuid, String targetServer, List<String> commands) {
        repository.addPendingReward(playerUuid, targetServer, encodeCommands(commands));
    }

    private String normalizeTarget(String target) {
        if (target == null || target.isBlank() || "current".equalsIgnoreCase(target)) {
            return config.serverId();
        }
        return target.toLowerCase(Locale.ROOT);
    }

    private List<String> resolveCommands(List<String> commands, Player player, String caseId, String rewardId) {
        List<String> resolved = new ArrayList<>();
        for (String command : commands) {
            resolved.add(applyPlaceholders(command, player.getName(), player.getUniqueId(), caseId, rewardId));
        }
        return resolved;
    }

    private String applyPlaceholders(String command, String playerName, UUID playerUuid, String caseId, String rewardId) {
        return command
            .replace("%player%", playerName)
            .replace("%uuid%", playerUuid.toString())
            .replace("%case%", caseId)
            .replace("%reward%", rewardId)
            .replace("%server%", config.serverId());
    }

    static String encodeCommands(List<String> commands) {
        return String.join("\n", commands);
    }

    static List<String> decodeCommands(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        return List.of(encoded.split("\n"));
    }
}
