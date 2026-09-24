package com.foxaria.security;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.model.SecurityIncident;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.api.service.SecurityService;
import org.bukkit.command.CommandSender;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

public final class JdbcSecurityService implements SecurityService {

    private final DatabaseGateway database;
    private final AuditService audits;
    private final ConcurrentMap<String, Long> commandWindows = new ConcurrentHashMap<>();
    private final ConcurrentSkipListSet<UUID> grimVerboseWatchers = new ConcurrentSkipListSet<>();
    private final long windowMillis;
    private final int maxHits;

    public JdbcSecurityService(DatabaseGateway database, AuditService audits, long windowMillis, int maxHits) {
        this.database = database;
        this.audits = audits;
        this.windowMillis = windowMillis;
        this.maxHits = maxHits;
    }

    @Override
    public boolean allowCommand(CommandSender sender, String commandKey) {
        String key = sender.getName() + ":" + commandKey;
        long now = System.currentTimeMillis();
        long bucket = now / windowMillis;
        String bucketKey = key + ":" + bucket;
        long hits = commandWindows.merge(bucketKey, 1L, Long::sum);
        if (hits > maxHits) {
            recordEvent(null, "COMMAND_RATE_LIMIT", sender.getName() + " exceeded command limit for " + commandKey);
            return false;
        }
        return true;
    }

    @Override
    public CompletableFuture<Void> recordEvent(UUID actorUuid, String type, String summary) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "INSERT INTO fx_security_events (id, actor_uuid, event_type, summary, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                actorUuid == null ? null : actorUuid.toString(),
                type,
                summary,
                System.currentTimeMillis()
            )) {
                statement.executeUpdate();
            }
        }).thenRun(() -> audits.append(new AuditEvent(
            "SECURITY_EVENT",
            actorUuid,
            null,
            actorUuid == null ? "system" : actorUuid.toString(),
            null,
            summary,
            Map.of("type", type),
            System.currentTimeMillis()
        )));
    }

    @Override
    public CompletableFuture<List<SecurityIncident>> recent(UUID actorUuid, int limit) {
        return database.query(connection -> {
            List<SecurityIncident> incidents = new ArrayList<>();
            try (PreparedStatement statement = actorUuid == null
                ? database.prepare(connection, "SELECT * FROM fx_security_events ORDER BY created_at DESC LIMIT ?", limit)
                : database.prepare(connection, "SELECT * FROM fx_security_events WHERE actor_uuid = ? ORDER BY created_at DESC LIMIT ?", actorUuid.toString(), limit);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    incidents.add(new SecurityIncident(
                        resultSet.getString("id"),
                        resultSet.getString("actor_uuid") == null ? null : UUID.fromString(resultSet.getString("actor_uuid")),
                        resultSet.getString("event_type"),
                        resultSet.getString("summary"),
                        resultSet.getLong("created_at")
                    ));
                }
            }
            return incidents;
        });
    }

    public boolean toggleVerbose(UUID playerUuid) {
        if (!grimVerboseWatchers.add(playerUuid)) {
            grimVerboseWatchers.remove(playerUuid);
            return false;
        }
        return true;
    }

    public void ingestGrimAlert(UUID actorUuid, String playerName, String check, String details) {
        recordEvent(actorUuid, "GRIM_ALERT", check + " | " + details);
        String line = "[Grim] " + playerName + " | " + check + " | " + details;
        grimVerboseWatchers.forEach(uuid -> {
            org.bukkit.entity.Player watcher = org.bukkit.Bukkit.getPlayer(uuid);
            if (watcher != null && watcher.hasPermission("foxaria.security.verbose")) {
                watcher.sendMessage(line);
            }
        });
    }

    public void ingestGrimPunish(UUID actorUuid, String playerName, String check, String details) {
        recordEvent(actorUuid, "GRIM_PUNISH", check + " | " + details);
    }
}
