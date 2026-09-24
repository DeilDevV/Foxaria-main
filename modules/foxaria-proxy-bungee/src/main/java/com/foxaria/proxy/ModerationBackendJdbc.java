package com.foxaria.proxy;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;

import java.io.File;
import java.nio.file.Path;

/**
 * Доступ к той же БД, что Foxaria на Paper ({@code fx_punishments}): те же параметры, что {@code database} в {@code plugins/Foxaria/config.yml}.
 */
public final class ModerationBackendJdbc {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private ModerationBackendJdbc(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
        this.username = username;
        this.password = password;
    }

    public boolean isEnabled() {
        return !jdbcUrl.isBlank();
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    /**
     * Приоритет: переменная окружения {@code FOXARIA_MODERATION_JDBC_URL}, затем {@code moderation.backend-database.jdbc-url},
     * затем сборка из {@code moderation.backend-database.type} и полей host/port/name/username/password (как у Paper).
     */
    public static ModerationBackendJdbc resolve(Plugin plugin, Configuration cfg) {
        if (!cfg.getBoolean("moderation.backend-database.enabled", true)) {
            return new ModerationBackendJdbc("", null, null);
        }
        String envUrl = System.getenv("FOXARIA_MODERATION_JDBC_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            String eu = System.getenv("FOXARIA_MODERATION_JDBC_USER");
            String ep = System.getenv("FOXARIA_MODERATION_JDBC_PASSWORD");
            return new ModerationBackendJdbc(envUrl.trim(), blankToNull(eu), ep);
        }
        String direct = cfg.getString("moderation.backend-database.jdbc-url", "");
        if (direct != null && !direct.isBlank()) {
            return new ModerationBackendJdbc(direct.trim(), null, null);
        }
        String type = cfg.getString("moderation.backend-database.type", "").trim().toLowerCase();
        if (type.isEmpty()) {
            return new ModerationBackendJdbc("", null, null);
        }
        String user = cfg.getString("moderation.backend-database.username",
            cfg.getString("moderation.backend-database.user", "root"));
        String pass = cfg.getString("moderation.backend-database.password", "");
        return switch (type) {
            case "mysql" -> new ModerationBackendJdbc(buildMysqlUrl(cfg), user, pass);
            case "postgresql", "postgres" -> new ModerationBackendJdbc(buildPostgresUrl(cfg), user, pass);
            case "sqlite" -> new ModerationBackendJdbc(buildSqliteUrl(plugin, cfg), null, null);
            default -> new ModerationBackendJdbc("", null, null);
        };
    }

    private static String buildMysqlUrl(Configuration cfg) {
        String host = cfg.getString("moderation.backend-database.host", "127.0.0.1");
        int port = cfg.getInt("moderation.backend-database.port", 3306);
        String name = cfg.getString("moderation.backend-database.name", "foxaria");
        return "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static String buildPostgresUrl(Configuration cfg) {
        String host = cfg.getString("moderation.backend-database.host", "127.0.0.1");
        int port = cfg.getInt("moderation.backend-database.port", 5432);
        String name = cfg.getString("moderation.backend-database.name", "foxaria");
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    private static String buildSqliteUrl(Plugin plugin, Configuration cfg) {
        String file = cfg.getString("moderation.backend-database.file", "foxaria.db");
        Path p = Path.of(file);
        if (!p.isAbsolute()) {
            p = plugin.getDataFolder().toPath().resolve(file);
        }
        return "jdbc:sqlite:" + p.toAbsolutePath();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
