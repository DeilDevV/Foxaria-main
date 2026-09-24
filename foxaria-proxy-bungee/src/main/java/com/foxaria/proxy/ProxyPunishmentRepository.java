package com.foxaria.proxy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ProxyPunishmentRepository {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public ProxyPunishmentRepository(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        ProxyJdbcSupport.ensureDriverLoaded(jdbcUrl);
    }

    private Connection openConnection() throws Exception {
        return username == null
            ? DriverManager.getConnection(jdbcUrl)
            : DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
    }

    public void ensureSchema() {
        String sql = jdbcUrl != null && jdbcUrl.toLowerCase().startsWith("jdbc:mysql:") ? """
            CREATE TABLE IF NOT EXISTS proxy_punishments (
              id VARCHAR(64) PRIMARY KEY,
              target_uuid VARCHAR(36) NOT NULL,
              actor_name VARCHAR(64),
              type VARCHAR(32) NOT NULL,
              reason_code VARCHAR(64),
              reason_title TEXT NOT NULL,
              reason_description TEXT NOT NULL,
              created_at BIGINT NOT NULL,
              expires_at BIGINT NOT NULL,
              active INTEGER NOT NULL
            )
            """ : """
            CREATE TABLE IF NOT EXISTS proxy_punishments (
              id TEXT PRIMARY KEY,
              target_uuid TEXT NOT NULL,
              actor_name TEXT,
              type TEXT NOT NULL,
              reason_code TEXT,
              reason_title TEXT NOT NULL,
              reason_description TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL,
              active INTEGER NOT NULL
            )
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
        try (Connection c = openConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE proxy_punishments ADD COLUMN target_name TEXT");
        } catch (Exception ignored) {
            /* колонка уже есть */
        }
    }

    public void add(PunishmentRecord record) {
        String sql = """
            INSERT INTO proxy_punishments (id, target_uuid, target_name, actor_name, type, reason_code, reason_title, reason_description, created_at, expires_at, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, record.id());
            ps.setString(2, record.targetUuid().toString());
            ps.setString(3, record.targetName());
            ps.setString(4, record.actorName());
            ps.setString(5, record.type());
            ps.setString(6, record.reasonCode());
            ps.setString(7, record.reasonTitle());
            ps.setString(8, record.reasonDescription());
            ps.setLong(9, record.createdAt());
            ps.setLong(10, record.expiresAt());
            ps.setInt(11, record.active() ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("FoxariaProxy").log(java.util.logging.Level.WARNING,
                "proxy_punishments INSERT failed (бан не сохранён в локальной БД прокси): " + e.getMessage(), e);
        }
    }

    public PunishmentRecord activeByTypes(UUID playerUuid, long now, String... types) {
        String sql = "SELECT * FROM proxy_punishments WHERE LOWER(TRIM(target_uuid))=LOWER(TRIM(?)) AND active=1 ORDER BY created_at DESC";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PunishmentRecord r = read(rs);
                    if (!matchesType(r.type(), types)) {
                        continue;
                    }
                    if (r.expiresAt() > 0 && r.expiresAt() <= now) {
                        deactivate(r.id());
                        continue;
                    }
                    return r;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean matchesType(String got, String... types) {
        if (got == null) {
            return false;
        }
        for (String t : types) {
            if (got.equalsIgnoreCase(t)) {
                return true;
            }
        }
        return false;
    }

    private void deactivate(String id) {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE proxy_punishments SET active=0 WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    /**
     * Снимает все активные строки с перечисленными id (дубликаты одной причины / несколько записей).
     */
    public int deactivateByRecordIds(Collection<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return 0;
        }
        List<String> ids = new ArrayList<>(recordIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "UPDATE proxy_punishments SET active=0 WHERE active=1 AND id IN (" + placeholders + ")";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(i + 1, ids.get(i));
            }
            return ps.executeUpdate();
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("FoxariaProxy").log(java.util.logging.Level.WARNING,
                "proxy_punishments deactivateByRecordIds failed: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Снимает все активные записи для UUID и/или совпадения nick (как /unban ник в Essentials).
     * Только аккаунт (UUID/ник), без IP.
     */
    public int deactivateAllMatching(List<UUID> candidateUuids, String targetDisplayName) {
        boolean hasUuids = candidateUuids != null && !candidateUuids.isEmpty();
        boolean hasName = targetDisplayName != null && !targetDisplayName.isBlank();
        if (!hasUuids && !hasName) {
            return 0;
        }
        StringBuilder sb = new StringBuilder("UPDATE proxy_punishments SET active=0 WHERE active=1 AND (");
        List<Object> params = new ArrayList<>();
        if (hasUuids) {
            sb.append("LOWER(TRIM(target_uuid)) IN (");
            for (int i = 0; i < candidateUuids.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("?");
                params.add(candidateUuids.get(i).toString().toLowerCase(Locale.ROOT));
            }
            sb.append(")");
        }
        if (hasName) {
            if (hasUuids) {
                sb.append(" OR ");
            }
            sb.append("(target_name IS NOT NULL AND lower(trim(target_name)) = lower(trim(?)))");
            params.add(targetDisplayName.trim());
        }
        sb.append(")");
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate();
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("FoxariaProxy").log(java.util.logging.Level.WARNING,
                "proxy_punishments UPDATE deactivate failed: " + e.getMessage(), e);
            return 0;
        }
    }

    public int deactivateAllActiveFor(UUID targetUuid) {
        return deactivateAllMatching(List.of(targetUuid), null);
    }

    public List<PunishmentRecord> listActiveFor(UUID targetUuid, long now) {
        List<PunishmentRecord> out = new ArrayList<>();
        String sql = "SELECT * FROM proxy_punishments WHERE LOWER(TRIM(target_uuid))=LOWER(TRIM(?)) AND active=1 ORDER BY created_at DESC";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PunishmentRecord r = read(rs);
                    if (r.expiresAt() > 0 && r.expiresAt() <= now) {
                        continue;
                    }
                    out.add(r);
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("FoxariaProxy").log(java.util.logging.Level.WARNING,
                "proxy_punishments SELECT failed: " + e.getMessage(), e);
        }
        return out;
    }

    /**
     * Все активные строки SQLite по аккаунту для /unpunish: несколько раундов, пока множество UUID расширяется
     * (ник → другой target_uuid → ещё строки). Иначе остаётся «второй» бан с UUID, не попавшим в Mojang/offline.
     */
    public List<PunishmentRecord> listAllActiveForUnpunish(List<UUID> seedUuids, String targetDisplayName, long now) {
        LinkedHashSet<UUID> universe = new LinkedHashSet<>();
        if (seedUuids != null) {
            for (UUID u : seedUuids) {
                if (u != null) {
                    universe.add(u);
                }
            }
        }
        String nameTrim = targetDisplayName != null ? targetDisplayName.trim() : "";
        boolean hasName = !nameTrim.isBlank();
        LinkedHashMap<String, PunishmentRecord> byId = new LinkedHashMap<>();

        for (int round = 0; round < 16; round++) {
            int beforeIds = byId.size();
            int beforeUniverse = universe.size();
            mergeActiveBatch(universe, nameTrim, hasName, now, byId);
            if (byId.size() == beforeIds && universe.size() == beforeUniverse) {
                break;
            }
        }
        return new ArrayList<>(byId.values());
    }

    private void mergeActiveBatch(
        LinkedHashSet<UUID> universe,
        String nameTrim,
        boolean hasName,
        long now,
        Map<String, PunishmentRecord> byId
    ) {
        boolean hasUuids = !universe.isEmpty();
        if (!hasUuids && !hasName) {
            return;
        }
        // Срок не фильтруем в SQL: старые строки могли иметь NULL/нестандартный expires_at — тогда условие
        // (expires_at=0 OR expires_at>?) отсекает строку, а activeByTypes всё ещё видит бан (getLong(NULL)=0).
        StringBuilder sql = new StringBuilder("SELECT * FROM proxy_punishments WHERE active=1 AND (");
        List<Object> params = new ArrayList<>();
        if (hasUuids) {
            sql.append("LOWER(TRIM(target_uuid)) IN (");
            List<UUID> list = new ArrayList<>(universe);
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sql.append(",");
                }
                sql.append("?");
                params.add(list.get(i).toString().toLowerCase(Locale.ROOT));
            }
            sql.append(")");
        }
        if (hasName) {
            if (hasUuids) {
                sql.append(" OR ");
            }
            sql.append("(target_name IS NOT NULL AND lower(trim(target_name)) = lower(trim(?)))");
            params.add(nameTrim);
        }
        sql.append(") ORDER BY created_at DESC");
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PunishmentRecord r = read(rs);
                    if (r.expiresAt() > 0 && r.expiresAt() <= now) {
                        continue;
                    }
                    byId.putIfAbsent(r.id(), r);
                    universe.add(r.targetUuid());
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("FoxariaProxy").log(java.util.logging.Level.WARNING,
                "proxy_punishments SELECT unpunish batch failed: " + e.getMessage(), e);
        }
    }

    /**
     * Активные санкции по нику на момент наказания (без привязки к UUID — как в Essentials).
     */
    public List<PunishmentRecord> listActiveByTargetName(String targetDisplayName, long now) {
        if (targetDisplayName == null || targetDisplayName.isBlank()) {
            return List.of();
        }
        List<PunishmentRecord> out = new ArrayList<>();
        String sql = """
            SELECT * FROM proxy_punishments
            WHERE active=1 AND target_name IS NOT NULL AND lower(trim(target_name)) = lower(trim(?))
            ORDER BY created_at DESC
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetDisplayName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PunishmentRecord r = read(rs);
                    if (r.expiresAt() > 0 && r.expiresAt() <= now) {
                        continue;
                    }
                    out.add(r);
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("FoxariaProxy").log(java.util.logging.Level.WARNING,
                "proxy_punishments SELECT by name failed: " + e.getMessage(), e);
        }
        return out;
    }

    public List<PunishmentRecord> listActiveForAny(Iterable<UUID> candidateUuids, long now) {
        Map<String, PunishmentRecord> byId = new LinkedHashMap<>();
        for (UUID u : candidateUuids) {
            if (u == null) {
                continue;
            }
            for (PunishmentRecord r : listActiveFor(u, now)) {
                byId.putIfAbsent(r.id(), r);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Активные баны на прокси (SQLite) для HTTP /api/bans и страницы банлиста.
     */
    public List<PunishmentRecord> listActiveNetworkBans(long now) {
        List<PunishmentRecord> out = new ArrayList<>();
        String sql = """
            SELECT * FROM proxy_punishments
            WHERE active=1 AND (upper(type)='BAN' OR upper(type)='TEMPBAN')
            ORDER BY created_at DESC
            """;
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PunishmentRecord r = read(rs);
                if (r.expiresAt() > 0 && r.expiresAt() <= now) {
                    continue;
                }
                out.add(r);
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("FoxariaProxy").log(java.util.logging.Level.WARNING,
                "proxy_punishments listActiveNetworkBans failed: " + e.getMessage(), e);
        }
        return out;
    }

    private PunishmentRecord read(ResultSet rs) throws Exception {
        String tn = null;
        try {
            tn = rs.getString("target_name");
        } catch (Exception ignored) {
        }
        return new PunishmentRecord(
            rs.getString("id"),
            UUID.fromString(rs.getString("target_uuid")),
            tn,
            rs.getString("actor_name"),
            rs.getString("type"),
            rs.getString("reason_code"),
            rs.getString("reason_title"),
            rs.getString("reason_description"),
            rs.getLong("created_at"),
            rs.getLong("expires_at"),
            rs.getInt("active") == 1
        );
    }

    public record PunishmentRecord(
        String id,
        UUID targetUuid,
        String targetName,
        String actorName,
        String type,
        String reasonCode,
        String reasonTitle,
        String reasonDescription,
        long createdAt,
        long expiresAt,
        boolean active
    ) {
    }
}
