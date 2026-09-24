package com.foxaria.core.service;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatTagService {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final AuditService audits;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLogoutPenalty = ConcurrentHashMap.newKeySet();

    public CombatTagService(JavaPlugin plugin, ConfigService configs, AuditService audits) {
        this.plugin = plugin;
        this.configs = configs;
        this.audits = audits;
    }

    public void tag(Player attacker, Player victim) {
        long expiresAt = System.currentTimeMillis() + (configs.main().getLong("combat-tag.duration-seconds", 15L) * 1000L);
        taggedUntil.put(attacker.getUniqueId(), expiresAt);
        taggedUntil.put(victim.getUniqueId(), expiresAt);

        audits.append(new AuditEvent(
            "COMBAT_TAG_APPLIED",
            attacker.getUniqueId(),
            victim.getUniqueId(),
            attacker.getName(),
            victim.getName(),
            "Combat tag applied to both players",
            Map.of("durationSeconds", String.valueOf(configs.main().getLong("combat-tag.duration-seconds", 15L))),
            System.currentTimeMillis()
        ));
    }

    public boolean isTagged(UUID playerUuid) {
        Long expiresAt = taggedUntil.get(playerUuid);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            taggedUntil.remove(playerUuid);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID playerUuid) {
        Long expiresAt = taggedUntil.get(playerUuid);
        if (expiresAt == null) {
            return 0L;
        }
        return Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
    }

    public void handleQuit(Player player) {
        if (!isTagged(player.getUniqueId()) || !configs.main().getBoolean("combat-tag.kill-on-logout", true)) {
            return;
        }
        pendingLogoutPenalty.add(player.getUniqueId());
        audits.append(new AuditEvent(
            "COMBAT_LOG_PENALTY",
            player.getUniqueId(),
            player.getUniqueId(),
            player.getName(),
            player.getName(),
            "Player logged out while combat tagged",
            Map.of(),
            System.currentTimeMillis()
        ));
    }

    public void handleJoin(Player player) {
        if (!pendingLogoutPenalty.remove(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                player.setHealth(0.0D);
            }
        });
    }
}
