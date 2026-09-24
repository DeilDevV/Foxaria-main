package com.foxaria.proxy;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;

import java.io.File;

final class ProxyJdbcSupport {

    record Settings(String jdbcUrl, String username, String password) {
        boolean isMySql() {
            return jdbcUrl != null && jdbcUrl.toLowerCase().startsWith("jdbc:mysql:");
        }
    }

    private ProxyJdbcSupport() {
    }

    static Settings resolve(Plugin plugin, Configuration cfg, String section) {
        String directUrl = cfg.getString(section + ".jdbc-url", "").trim();
        String user = blankToNull(cfg.getString(section + ".username", cfg.getString(section + ".user", "root")));
        String pass = cfg.getString(section + ".password", "");
        if (!directUrl.isBlank()) {
            return new Settings(directUrl, user, pass);
        }

        String type = cfg.getString(section + ".type", "sqlite").trim().toLowerCase();
        return switch (type) {
            case "mysql" -> new Settings(
                "jdbc:mysql://"
                    + cfg.getString(section + ".host", "127.0.0.1") + ":"
                    + cfg.getInt(section + ".port", 3306) + "/"
                    + cfg.getString(section + ".name", "foxaria")
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8",
                user,
                pass
            );
            case "postgresql", "postgres" -> new Settings(
                "jdbc:postgresql://"
                    + cfg.getString(section + ".host", "127.0.0.1") + ":"
                    + cfg.getInt(section + ".port", 5432) + "/"
                    + cfg.getString(section + ".name", "foxaria"),
                user == null ? "postgres" : user,
                pass
            );
            default -> {
                String file = cfg.getString(section + ".sqlite-file", cfg.getString(section + ".file", "foxaria-proxy.db"));
                File dbFile = new File(file);
                if (!dbFile.isAbsolute()) {
                    dbFile = new File(plugin.getDataFolder(), file);
                }
                yield new Settings("jdbc:sqlite:" + dbFile.getAbsolutePath(), null, null);
            }
        };
    }

    static void ensureDriverLoaded(String jdbcUrl) {
        try {
            String lower = jdbcUrl == null ? "" : jdbcUrl.toLowerCase();
            if (lower.startsWith("jdbc:mysql:")) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                return;
            }
            if (lower.startsWith("jdbc:postgresql:")) {
                Class.forName("org.postgresql.Driver");
                return;
            }
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing JDBC driver for " + jdbcUrl, e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
