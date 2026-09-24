package com.foxaria.hubguard;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Чтение группы из БД FoxariaProxy и префикса из конфига (лобби без полного Foxaria).
 */
public final class HubRankBridge {

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, String> resolvedGroup = new ConcurrentHashMap<>();
    /** Строка префикса для чата с FoxariaProxy (UTF-16), без SQLite — авторитетнее кэша и YAML. */
    private final ConcurrentHashMap<UUID, String> proxyChatPrefix = new ConcurrentHashMap<>();

    public HubRankBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns the resolved rank group for this player (cached after {@link #warmCache}). */
    public String group(Player player) {
        return resolvedGroup.getOrDefault(player.getUniqueId(),
            plugin.getConfig().getString("rank-bridge.fallback-group", "default"));
    }

    public void warmCache(Player player) {
        if (!plugin.getConfig().getBoolean("rank-bridge.enabled", false)) {
            return;
        }
        resolvedGroup.put(player.getUniqueId(), resolveGroup(player.getUniqueId(), plugin.getConfig()));
    }

    public void forget(Player player) {
        UUID id = player.getUniqueId();
        resolvedGroup.remove(id);
        proxyChatPrefix.remove(id);
    }

    /**
     * Значение приходит по каналу foxaria:proxy ({@code ChatPrefixSync}).
     */
    public void setProxyChatPrefix(UUID uuid, String legacyAmpersandPrefix) {
        if (uuid == null) {
            return;
        }
        if (legacyAmpersandPrefix == null || legacyAmpersandPrefix.isBlank()) {
            proxyChatPrefix.remove(uuid);
        } else {
            proxyChatPrefix.put(uuid, legacyAmpersandPrefix);
        }
    }

    /** Есть ли синхронизированный с прокси префикс — тогда не применять «лечение» mojibake к SQLite/YAML. */
    public boolean hasProxyChatPrefix(Player player) {
        return proxyChatPrefix.containsKey(player.getUniqueId());
    }

    public boolean shouldNormalizeMojibakePrefix() {
        return plugin.getConfig().getBoolean("rank-bridge.normalize-mojibake-prefix", false);
    }

    /**
     * &-префикс для чата (часть до &8|), пусто если выключено или ошибка.
     */
    public String chatPrefix(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("rank-bridge.enabled", false)) {
            return "";
        }
        UUID id = player.getUniqueId();
        String fromProxy = proxyChatPrefix.get(id);
        if (fromProxy != null && !fromProxy.isBlank()) {
            return fromProxy;
        }
        String fallback = cfg.getString("rank-bridge.fallback-group", "default");
        String group = resolvedGroup.computeIfAbsent(id, u -> resolveGroup(u, cfg));
        String raw = prefixYaml(cfg, group, fallback);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return stripBar(raw);
    }

    private static String prefixYaml(FileConfiguration cfg, String group, String fallback) {
        String g = group == null ? "" : group.trim();
        String lower = g.toLowerCase(Locale.ROOT);
        String raw = cfg.getString("rank-bridge.groups." + lower + ".prefix", "");
        if (raw == null || raw.isBlank()) {
            raw = cfg.getString("rank-bridge.groups." + g + ".prefix", "");
        }
        if (raw == null || raw.isBlank()) {
            raw = cfg.getString("rank-bridge.groups." + fallback + ".prefix", "");
        }
        return raw;
    }

    private static String stripBar(String prefix) {
        int idx = prefix.indexOf("&8|");
        if (idx > 0) {
            return prefix.substring(0, idx).trim();
        }
        return prefix.trim();
    }

    private String resolveGroup(UUID uuid, FileConfiguration cfg) {
        String fallback = cfg.getString("rank-bridge.fallback-group", "default");
        String table = cfg.getString("rank-bridge.table", "proxy_privileges");
        String sql = "SELECT primary_group, temp_group, temp_expires_at FROM " + table + " WHERE player_uuid=?";
        try (Connection c = openRankBridgeConnection(cfg);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return fallback;
                }
                String primary = rs.getString("primary_group");
                String temp = rs.getString("temp_group");
                long expiresRaw = rs.getLong("temp_expires_at");
                boolean expiresNull = rs.wasNull();
                Long expires = expiresNull ? null : expiresRaw;
                if (temp != null && !temp.isBlank() && expires != null && expires > System.currentTimeMillis()) {
                    return temp;
                }
                return primary == null || primary.isBlank() ? fallback : primary;
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("rank-bridge: " + ex.getMessage());
            return fallback;
        }
    }

    private Connection openRankBridgeConnection(FileConfiguration cfg) throws Exception {
        String direct = cfg.getString("rank-bridge.jdbc-url", "").trim();
        String user = cfg.getString("rank-bridge.username", cfg.getString("rank-bridge.user", "root"));
        String pass = cfg.getString("rank-bridge.password", "");
        if (!direct.isBlank()) {
            ensureDriverLoaded(direct);
            return DriverManager.getConnection(direct, user, pass);
        }

        String type = cfg.getString("rank-bridge.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if ("mysql".equals(type)) {
            String jdbcUrl = "jdbc:mysql://"
                + cfg.getString("rank-bridge.host", "127.0.0.1") + ":"
                + cfg.getInt("rank-bridge.port", 3306) + "/"
                + cfg.getString("rank-bridge.name", "foxaria")
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";
            ensureDriverLoaded(jdbcUrl);
            return DriverManager.getConnection(jdbcUrl, user, pass);
        }
        if ("postgresql".equals(type) || "postgres".equals(type)) {
            String jdbcUrl = "jdbc:postgresql://"
                + cfg.getString("rank-bridge.host", "127.0.0.1") + ":"
                + cfg.getInt("rank-bridge.port", 5432) + "/"
                + cfg.getString("rank-bridge.name", "foxaria");
            ensureDriverLoaded(jdbcUrl);
            return DriverManager.getConnection(jdbcUrl, user, pass);
        }

        File dbFile = findProxySqlite(cfg);
        if (dbFile == null || !dbFile.isFile()) {
            throw new IllegalStateException("sqlite not found (tried primary + extra paths + server root)");
        }
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        ensureDriverLoaded(jdbcUrl);
        return DriverManager.getConnection(jdbcUrl);
    }

    private void ensureDriverLoaded(String jdbcUrl) throws ClassNotFoundException {
        String lower = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mysql:")) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return;
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            Class.forName("org.postgresql.Driver");
            return;
        }
        Class.forName("org.sqlite.JDBC");
    }

    private File resolveDbFile(String relative, File baseDir) {
        Path path = Path.of(relative);
        if (path.isAbsolute()) {
            return path.toFile();
        }
        return new File(baseDir, relative).getAbsoluteFile();
    }

    private File serverRoot() {
        File data = plugin.getDataFolder();
        File plugins = data.getParentFile();
        return plugins != null ? plugins.getParentFile() : data;
    }

    /** Ищем foxaria-proxy.db: основной путь, extra из конфига, типовые места от dataFolder и от корня сервера. */
    private File findProxySqlite(FileConfiguration cfg) {
        String primary = cfg.getString("rank-bridge.sqlite-file",
            "../proxy-bungeecord/plugins/FoxariaProxy/foxaria-proxy.db");
        List<String> paths = new ArrayList<>();
        paths.add(primary);
        paths.addAll(cfg.getStringList("rank-bridge.sqlite-extra-paths"));
        paths.add("plugins/FoxariaProxy/foxaria-proxy.db");
        paths.add("../proxy-bungeecord/plugins/FoxariaProxy/foxaria-proxy.db");
        paths.add("../bungeecord/plugins/FoxariaProxy/foxaria-proxy.db");
        paths.add("../BungeeCord/plugins/FoxariaProxy/foxaria-proxy.db");
        File dataFolder = plugin.getDataFolder();
        File root = serverRoot();
        // Сначала корень сервера: путь plugins/FoxariaProxy/... относится к нему, а не к plugins/FoxariaHubGuard/
        File[] bases = { root, dataFolder };
        for (String rel : paths) {
            if (rel == null || rel.isBlank()) {
                continue;
            }
            for (File base : bases) {
                if (base == null) {
                    continue;
                }
                File f = resolveDbFile(rel, base).getAbsoluteFile();
                if (f.isFile()) {
                    return f;
                }
            }
        }
        return resolveDbFile(primary, dataFolder).getAbsoluteFile();
    }
}
