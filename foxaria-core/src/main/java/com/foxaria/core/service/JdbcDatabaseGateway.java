package com.foxaria.core.service;

import com.foxaria.api.MigrationScript;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.DatabaseGateway;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class JdbcDatabaseGateway implements DatabaseGateway {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final Logger logger;
    private Dialect dialect = Dialect.SQLITE;
    private HikariDataSource dataSource;
    private ExecutorService executor;

    public JdbcDatabaseGateway(JavaPlugin plugin, ConfigService configs, Logger logger) {
        this.plugin = plugin;
        this.configs = configs;
        this.logger = logger;
    }

    @Override
    public void start() {
        HikariConfig hikari = new HikariConfig();
        String type = configs.main().getString("database.type", "sqlite").toLowerCase();
        switch (type) {
            case "mysql" -> {
                dialect = Dialect.MYSQL;
                hikari.setJdbcUrl("jdbc:mysql://"
                    + configs.main().getString("database.host", "127.0.0.1") + ":"
                    + configs.main().getInt("database.port", 3306) + "/"
                    + configs.main().getString("database.name", "foxaria")
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8");
                hikari.setUsername(configs.main().getString("database.username", "root"));
                hikari.setPassword(configs.main().getString("database.password", ""));
                hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            }
            case "postgresql", "postgres" -> {
                dialect = Dialect.POSTGRESQL;
                hikari.setJdbcUrl("jdbc:postgresql://"
                    + configs.main().getString("database.host", "127.0.0.1") + ":"
                    + configs.main().getInt("database.port", 5432) + "/"
                    + configs.main().getString("database.name", "foxaria"));
                hikari.setUsername(configs.main().getString("database.username", "postgres"));
                hikari.setPassword(configs.main().getString("database.password", ""));
                hikari.setDriverClassName("org.postgresql.Driver");
            }
            default -> {
                dialect = Dialect.SQLITE;
                String fileName = configs.main().getString("database.file", "foxaria.db");
                hikari.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().toPath().resolve(fileName));
                hikari.setDriverClassName("org.sqlite.JDBC");
            }
        }
        hikari.setMaximumPoolSize(configs.main().getInt("database.pool.maximum-size", 10));
        hikari.setMinimumIdle(configs.main().getInt("database.pool.minimum-idle", 2));
        hikari.setPoolName("FoxariaPool");
        dataSource = new HikariDataSource(hikari);
        executor = Executors.newFixedThreadPool(configs.main().getInt("database.executor-threads", 4));
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public void applyMigrations(List<MigrationScript> scripts) {
        try (Connection connection = dataSource.getConnection()) {
            boolean prevAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement init = connection.createStatement()) {
                init.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS fx_schema_history (
                        version INTEGER PRIMARY KEY,
                        description VARCHAR(255) NOT NULL,
                        applied_at BIGINT NOT NULL
                    )
                    """);
            }

            for (MigrationScript script : scripts.stream().sorted(Comparator.comparingInt(MigrationScript::version)).toList()) {
                if (isApplied(connection, script.version())) {
                    continue;
                }
                logger.info("Applying migration V" + script.version() + " - " + script.description());
                for (String sql : readSqlStatements(script.resourcePath())) {
                    String migratedSql = adaptSqlForDialect(sql);
                    if (migratedSql == null || migratedSql.isBlank()) {
                        continue;
                    }
                    // SQLite driver is sensitive to prepared DDL; execute raw statement per chunk.
                    try {
                        if (isSqlite(connection)) {
                            String[] alter = parseAlterAddColumn(migratedSql);
                            if (alter != null && columnExists(connection, alter[0], alter[1])) {
                                logger.warning("Migration V" + script.version() + ": column already exists, skipping " + alter[0] + "." + alter[1]);
                                continue;
                            }
                        }
                        try (Statement ddl = connection.createStatement()) {
                            ddl.execute(migratedSql);
                        }
                    } catch (Exception ex) {
                        if (isDuplicateColumn(ex)) {
                            logger.warning("Migration V" + script.version() + ": duplicate column, skipping statement.");
                            continue;
                        }
                        throw ex;
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO fx_schema_history (version, description, applied_at) VALUES (?, ?, ?)"
                )) {
                    insert.setInt(1, script.version());
                    insert.setString(2, script.description());
                    insert.setLong(3, System.currentTimeMillis());
                    insert.executeUpdate();
                }
                connection.commit();
            }
            connection.setAutoCommit(prevAutoCommit);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to apply Foxaria migrations", exception);
        }
    }

    private boolean isDuplicateColumn(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("duplicate column")
            || m.contains("duplicate column name")
            || m.contains("duplicate key name")
            || m.contains("already exists");
    }

    private boolean isSqlite(Connection connection) {
        try {
            String url = connection.getMetaData().getURL();
            return url != null && url.startsWith("jdbc:sqlite:");
        } catch (Exception ignored) {
            return false;
        }
    }

    /** @return [table, column] for ALTER TABLE ... ADD COLUMN ..., else null */
    private String[] parseAlterAddColumn(String sql) {
        if (sql == null) return null;
        String s = stripLeadingLineComments(sql).trim().replaceAll("\\s+", " ");
        String lower = s.toLowerCase();
        if (!lower.startsWith("alter table ")) return null;
        int addIdx = lower.indexOf(" add column ");
        if (addIdx < 0) return null;
        String tablePart = s.substring("alter table ".length(), addIdx).trim();
        String colPart = s.substring(addIdx + " add column ".length()).trim();
        if (tablePart.isEmpty() || colPart.isEmpty()) return null;
        // colPart may include type/constraints: take first token
        String col = colPart.split(" ")[0].trim();
        if (col.isEmpty()) return null;
        // strip quotes/backticks if any
        col = col.replace("`", "").replace("\"", "");
        tablePart = tablePart.replace("`", "").replace("\"", "");
        return new String[]{ tablePart, col };
    }

    private String stripLeadingLineComments(String sql) {
        String[] lines = sql.split("\\R");
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (String line : lines) {
            String t = line.trim();
            if (!started) {
                if (t.isEmpty() || t.startsWith("--")) {
                    continue;
                }
                started = true;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private boolean columnExists(Connection connection, String table, String column) {
        try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null && name.equalsIgnoreCase(column)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public <T> CompletableFuture<T> query(SqlFunction<Connection, T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return work.apply(connection);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> execute(SqlConsumer<Connection> work) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                work.accept(connection);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    @Override
    public PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws java.sql.SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
        return statement;
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    private boolean isApplied(Connection connection, int version) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT version FROM fx_schema_history WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private List<String> readSqlStatements(String resourcePath) throws Exception {
        InputStream inputStream = plugin.getResource(resourcePath);
        if (inputStream == null) {
            throw new IllegalStateException("Migration resource not found: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            // Strip SQL line comments to avoid SQLite executing bare "-- ..." fragments after splitting by ';'.
            String content = reader.lines().map(line -> {
                String t = line.trim();
                if (t.startsWith("--")) {
                    return "";
                }
                int idx = line.indexOf("--");
                if (idx >= 0) {
                    return line.substring(0, idx);
                }
                return line;
            }).reduce("", (left, right) -> left + "\n" + right);
            return java.util.Arrays.stream(content.split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isBlank())
                .toList();
        }
    }

    private String adaptSqlForDialect(String sql) {
        if (sql == null || dialect != Dialect.MYSQL) {
            return sql;
        }
        String adapted = sql;
        adapted = adapted.replaceAll("(?i)INTEGER\\s+PRIMARY\\s+KEY\\s+AUTOINCREMENT", "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        adapted = adapted.replaceAll("(?i)INT\\s+PRIMARY\\s+KEY\\s+AUTOINCREMENT", "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        adapted = adapted.replaceAll("(?i)CREATE\\s+INDEX\\s+IF\\s+NOT\\s+EXISTS", "CREATE INDEX");
        adapted = rewriteInsertDoNothingForMySql(adapted);
        return adapted;
    }

    private String rewriteInsertDoNothingForMySql(String sql) {
        String trimmed = sql.trim();
        String lower = trimmed.toLowerCase();
        // Use regex-based search to handle any whitespace (spaces, newlines) before ON CONFLICT
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\s+on\\s+conflict\\s*\\(.*?\\)\\s+do\\s+nothing", java.util.regex.Pattern.DOTALL)
                .matcher(trimmed);
        if (!lower.startsWith("insert into ") || !m.find()) {
            return sql;
        }
        return "INSERT IGNORE" + trimmed.substring("INSERT".length(), m.start());
    }
}
