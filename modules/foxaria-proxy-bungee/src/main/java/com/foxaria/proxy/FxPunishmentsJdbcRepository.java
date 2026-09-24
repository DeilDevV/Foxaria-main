package com.foxaria.proxy;

import net.md_5.bungee.api.plugin.Plugin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Таблица {@code fx_punishments} на игровых серверах (Paper). Локальный SQLite прокси хранит дубликат в
 * {@code proxy_punishments}; без этого слоя /unpunish на прокси не видит баны, выданные с Paper.
 */
public final class FxPunishmentsJdbcRepository {

    private static final String[] MODERATION_TYPES = {"MUTE", "TEMPMUTE", "BAN", "TEMPBAN", "FREEZE"};

    /** 0 = не определено, 1 = Foxaria (target_uuid, created_at, expires_at), 2 = старая схема (uuid, start_time, end_time). */
    private volatile int fxPunishmentsSchema;

    private final Plugin plugin;
    private final ModerationBackendJdbc backend;

    public FxPunishmentsJdbcRepository(Plugin plugin, ModerationBackendJdbc backend) {
        this.plugin = plugin;
        this.backend = backend;
    }

    public boolean isEnabled() {
        return backend.isEnabled();
    }

    private Connection connect() throws Exception {
        String u = backend.username();
        if (u != null && !u.isBlank()) {
            String p = backend.password();
            return DriverManager.getConnection(backend.jdbcUrl(), u, p == null ? "" : p);
        }
        return DriverManager.getConnection(backend.jdbcUrl());
    }

    private int resolveFxPunishmentsSchema() {
        if (fxPunishmentsSchema != 0) {
            return fxPunishmentsSchema;
        }
        synchronized (this) {
            if (fxPunishmentsSchema != 0) {
                return fxPunishmentsSchema;
            }
            int mode = 1;
            try (Connection c = connect()) {
                boolean hasTarget = columnExists(c.getMetaData(), "fx_punishments", "target_uuid");
                boolean hasUuid = columnExists(c.getMetaData(), "fx_punishments", "uuid");
                if (hasTarget) {
                    mode = 1;
                } else if (hasUuid) {
                    mode = 2;
                }
            } catch (Exception ignored) {
                mode = 1;
            }
            fxPunishmentsSchema = mode;
            if (mode == 2) {
                plugin.getLogger().info("[moderation backend] fx_punishments: колонки uuid/start_time/end_time (совместимость со старыми БД).");
            }
            return fxPunishmentsSchema;
        }
    }

    private static boolean columnExists(DatabaseMetaData meta, String table, String column) {
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private String targetUuidColumnForWhere() {
        return resolveFxPunishmentsSchema() == 2 ? "uuid" : "target_uuid";
    }

    /**
     * Активные санкции в игровой БД; при ошибке соединения — {@link BackendSanctionsResult#queryFailed()}, а не пустой список.
     */
    public BackendSanctionsResult listActiveForBroadcastResult(List<UUID> candidateUuids, long nowMillis) {
        if (!isEnabled() || candidateUuids == null || candidateUuids.isEmpty()) {
            return BackendSanctionsResult.ok(List.of());
        }
        List<UUID> distinct = candidateUuids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return BackendSanctionsResult.ok(List.of());
        }
        String activePredicate = activeIsTrueSql();
        String placeholders = String.join(", ", distinct.stream().map(u -> "?").toList());
        int schema = resolveFxPunishmentsSchema();
        String selectList = schema == 2
            ? "id, uuid AS target_uuid, type, reason, start_time AS created_at, end_time AS expires_at"
            : "id, target_uuid, type, reason, created_at, expires_at";
        String uuidCol = schema == 2 ? "uuid" : "target_uuid";
        String orderCol = schema == 2 ? "start_time" : "created_at";
        String sql = "SELECT " + selectList
            + " FROM fx_punishments WHERE " + uuidCol + " IN (" + placeholders + ") AND " + activePredicate + " "
            + "ORDER BY " + orderCol + " DESC";
        Map<String, ProxyPunishmentRepository.PunishmentRecord> byId = new LinkedHashMap<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (UUID u : distinct) {
                ps.setString(i++, u.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    if (!matchesModerationType(type)) {
                        continue;
                    }
                    long expiresAt = rs.getLong("expires_at");
                    if (expiresAt > 0L && expiresAt <= nowMillis) {
                        continue;
                    }
                    UUID rowTarget = UUID.fromString(rs.getString("target_uuid"));
                    String id = rs.getString("id");
                    String reason = rs.getString("reason");
                    long createdAt = rs.getLong("created_at");
                    String title = reason == null || reason.isBlank() ? type : abbrev(reason, 96);
                    var rec = new ProxyPunishmentRepository.PunishmentRecord(
                        id,
                        rowTarget,
                        null,
                        "—",
                        type,
                        "",
                        title,
                        "",
                        createdAt,
                        expiresAt,
                        true
                    );
                    byId.putIfAbsent(id, rec);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[moderation backend] listActive failed: " + e.getMessage());
            return BackendSanctionsResult.failed(e.getMessage());
        }
        return BackendSanctionsResult.ok(new ArrayList<>(byId.values()));
    }

    public int deactivateAllModerationSanctionsFor(List<UUID> candidateUuids) {
        if (!isEnabled() || candidateUuids == null || candidateUuids.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID u : candidateUuids.stream().distinct().toList()) {
            total += deactivateAllModerationSanctions(u);
        }
        return total;
    }

    /**
     * Снимает все перечисленные строки по id (дубликаты одной причины и т.д.). Только аккаунт, без IP.
     */
    public int deactivateRecordsByIds(List<String> recordIds) {
        if (!isEnabled() || recordIds == null || recordIds.isEmpty()) {
            return 0;
        }
        List<String> ids = recordIds.stream().distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "UPDATE fx_punishments SET " + deactivateSetSql() + " WHERE " + activeIsTrueSql()
            + " AND id IN (" + placeholders + ")";
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(i + 1, ids.get(i));
            }
            return ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[moderation backend] deactivateRecordsByIds failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Снимает те же типы, что {@link com.foxaria.moderation.JdbcModerationService#unpunishAll}.
     */
    public int deactivateAllModerationSanctions(UUID targetUuid) {
        if (!isEnabled()) {
            return 0;
        }
        int total = 0;
        String setInactive = deactivateSetSql();
        String uuidCol = targetUuidColumnForWhere();
        String sql = "UPDATE fx_punishments SET " + setInactive + " WHERE " + uuidCol + " = ? AND type = ? AND " + activeIsTrueSql();
        try (Connection c = connect()) {
            for (String type : MODERATION_TYPES) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, targetUuid.toString());
                    ps.setString(2, type);
                    total += ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[moderation backend] deactivate failed: " + e.getMessage());
            return 0;
        }
        return total;
    }

    public void probeOnStartup() {
        if (!isEnabled()) {
            plugin.getLogger().info("Moderation backend (fx_punishments): не задан — скопируй параметры из database (Paper) в moderation.backend-database "
                + "или переменную FOXARIA_MODERATION_JDBC_URL; иначе /unpunish только по локальной БД прокси.");
            return;
        }
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM fx_punishments LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            plugin.getLogger().info("Moderation backend (fx_punishments): подключено к той же БД, что Paper — /unpunish снимает санкции на сети.");
        } catch (Exception e) {
            plugin.getLogger().severe("Moderation backend: не удалось подключиться к БД игровых серверов: " + e.getMessage()
                + " — сверь moderation.backend-database с database в plugins/Foxaria/config.yml на Paper.");
        }
    }

    private String activeIsTrueSql() {
        String url = backend.jdbcUrl();
        if (url.startsWith("jdbc:postgresql:")) {
            return "active IS TRUE";
        }
        return "active = TRUE";
    }

    private String deactivateSetSql() {
        String url = backend.jdbcUrl();
        if (url.startsWith("jdbc:postgresql:")) {
            return "active = FALSE";
        }
        return "active = 0";
    }

    private static boolean matchesModerationType(String type) {
        if (type == null) {
            return false;
        }
        String u = type.toUpperCase(Locale.ROOT);
        for (String t : MODERATION_TYPES) {
            if (t.equals(u)) {
                return true;
            }
        }
        return false;
    }

    private static String abbrev(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
