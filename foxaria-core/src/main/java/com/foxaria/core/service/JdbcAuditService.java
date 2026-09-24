package com.foxaria.core.service;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdbcAuditService implements AuditService {

    private final DatabaseGateway database;

    public JdbcAuditService(DatabaseGateway database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Void> append(AuditEvent event) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    INSERT INTO fx_audit_log (id, event_type, actor_uuid, target_uuid, actor_name, target_name, summary, metadata_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                java.util.UUID.randomUUID().toString(),
                event.type(),
                event.actorUuid() == null ? null : event.actorUuid().toString(),
                event.targetUuid() == null ? null : event.targetUuid().toString(),
                event.actorName(),
                event.targetName(),
                event.summary(),
                JsonMapCodec.encode(event.metadata()),
                event.createdAt()
            )) {
                statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<List<AuditEvent>> recent(UUID targetUuid, int limit) {
        return database.query(connection -> {
            List<AuditEvent> results = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_audit_log WHERE target_uuid = ? ORDER BY created_at DESC LIMIT ?",
                targetUuid.toString(),
                limit
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(new AuditEvent(
                        resultSet.getString("event_type"),
                        parseUuid(resultSet.getString("actor_uuid")),
                        parseUuid(resultSet.getString("target_uuid")),
                        resultSet.getString("actor_name"),
                        resultSet.getString("target_name"),
                        resultSet.getString("summary"),
                        JsonMapCodec.decode(resultSet.getString("metadata_json")),
                        resultSet.getLong("created_at")
                    ));
                }
            }
            return results;
        });
    }

    private UUID parseUuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private static final class JsonMapCodec {
        private JsonMapCodec() {
        }

        static String encode(Map<String, String> map) {
            if (map == null || map.isEmpty()) {
                return "{}";
            }
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append('"').append(escape(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(escape(entry.getValue())).append('"');
                first = false;
            }
            return builder.append('}').toString();
        }

        static Map<String, String> decode(String json) {
            Map<String, String> map = new HashMap<>();
            if (json == null || json.isBlank() || "{}".equals(json.trim())) {
                return map;
            }
            String content = json.trim();
            if (content.startsWith("{")) {
                content = content.substring(1);
            }
            if (content.endsWith("}")) {
                content = content.substring(0, content.length() - 1);
            }
            for (String entry : content.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                map.put(unescape(strip(parts[0])), unescape(strip(parts[1])));
            }
            return map;
        }

        private static String strip(String value) {
            String trimmed = value.trim();
            if (trimmed.startsWith("\"")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private static String unescape(String value) {
            return value.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
}
