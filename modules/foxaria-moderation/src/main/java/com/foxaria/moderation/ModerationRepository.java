package com.foxaria.moderation;

import com.foxaria.api.model.PunishmentRecord;
import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ModerationRepository {

    private final DatabaseGateway database;

    public ModerationRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Void> addPunishment(PunishmentRecord record) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                """
                    INSERT INTO fx_punishments (id, target_uuid, actor_uuid, type, reason, created_at, expires_at, active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                record.id(),
                record.targetUuid().toString(),
                record.actorUuid() == null ? null : record.actorUuid().toString(),
                record.type(),
                record.reason(),
                record.createdAt(),
                record.expiresAt(),
                record.active()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deactivateType(UUID targetUuid, String type) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_punishments SET active = FALSE WHERE target_uuid = ? AND type = ? AND active = TRUE",
                targetUuid.toString(),
                type
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deactivateTypes(UUID targetUuid, String... types) {
        return database.execute(connection -> {
            for (String type : types) {
                try (PreparedStatement statement = database.prepare(
                    connection,
                    "UPDATE fx_punishments SET active = FALSE WHERE target_uuid = ? AND type = ? AND active = TRUE",
                    targetUuid.toString(),
                    type
                )) {
                    statement.executeUpdate();
                }
            }
        });
    }

    public CompletableFuture<List<PunishmentRecord>> active(UUID targetUuid) {
        return database.query(connection -> {
            List<PunishmentRecord> records = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_punishments WHERE target_uuid = ? AND active = TRUE",
                targetUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(read(resultSet));
                }
            }
            return records;
        });
    }

    public CompletableFuture<List<PunishmentRecord>> history(UUID targetUuid) {
        return database.query(connection -> {
            List<PunishmentRecord> records = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_punishments WHERE target_uuid = ? ORDER BY created_at DESC LIMIT 50",
                targetUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(read(resultSet));
                }
            }
            return records;
        });
    }

    public CompletableFuture<Void> addReport(UUID reporter, UUID target, String reason) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "INSERT INTO fx_reports (id, reporter_uuid, target_uuid, reason, status, created_at) VALUES (?, ?, ?, ?, 'OPEN', ?)",
                UUID.randomUUID().toString(),
                reporter.toString(),
                target.toString(),
                reason,
                System.currentTimeMillis()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> addNote(UUID actor, UUID target, String note) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "INSERT INTO fx_staff_notes (id, target_uuid, actor_uuid, note, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                target.toString(),
                actor.toString(),
                note,
                System.currentTimeMillis()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<ReportEntry>> openReports() {
        return database.query(connection -> {
            List<ReportEntry> reports = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_reports WHERE status = 'OPEN' ORDER BY created_at ASC"
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    reports.add(new ReportEntry(
                        resultSet.getString("id"),
                        UUID.fromString(resultSet.getString("reporter_uuid")),
                        UUID.fromString(resultSet.getString("target_uuid")),
                        resultSet.getString("reason"),
                        resultSet.getString("status"),
                        resultSet.getLong("created_at")
                    ));
                }
            }
            return reports;
        });
    }

    public CompletableFuture<Void> closeReport(String reportId, UUID actorUuid) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "UPDATE fx_reports SET status = ? WHERE id = ?",
                "CLOSED:" + actorUuid,
                reportId
            )) {
                statement.executeUpdate();
            }
        });
    }

    private PunishmentRecord read(ResultSet resultSet) throws Exception {
        return new PunishmentRecord(
            resultSet.getString("id"),
            UUID.fromString(resultSet.getString("target_uuid")),
            resultSet.getString("actor_uuid") == null ? null : UUID.fromString(resultSet.getString("actor_uuid")),
            resultSet.getString("type"),
            resultSet.getString("reason"),
            resultSet.getLong("created_at"),
            resultSet.getLong("expires_at"),
            resultSet.getBoolean("active")
        );
    }

    public record ReportEntry(String id, UUID reporterUuid, UUID targetUuid, String reason, String status, long createdAt) {
    }
}
