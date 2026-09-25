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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

            for (MigrationScript script : scripts.stream()
                    .sorted(Comparator.comparingInt(MigrationScript::version))
                    .toList()) {
                if (isApplied(connection, script.version())) {
                    continue;
                }
                logger.info("Applying migration V" + script.version() + " - " + script.description());
                for (String sql : readSqlStatements(script.resourcePath())) {
                    String migratedSql = adaptSqlForDialect(sql);
                    if (migratedSql == null || migratedSql.isBlank()) {
                        continue;
                    }
                    try {
                        if (isSqliteConnection(connection)) {
                            String[] alter = parseAlterAddColumn(migratedSql);
                            if (alter != null && columnExists(connection, alter[0], alter[1])) {
                                logger.warning("Migration V" + script.version()
                                        + ": column already exists, skipping " + alter[0] + "." + alter[1]);
                                continue;
                            }
                        }
                        try (Statement ddl = connection.createStatement()) {
                            ddl.execute(migratedSql);
                        }
                    } catch (Exception ex) {
                        if (isDuplicateColumn(ex)) {
                            logger.warning("Migration V" + script.version()
                                    + ": duplicate column, skipping statement.");
                            continue;
                        }
                        throw ex;
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO fx_schema_history (version, description, applied_at) VALUES (?, ?, ?)")) {
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

    // -------------------------------------------------------------------------
    //  SQL dialect adaptation  (SQLite → MySQL)
    // -------------------------------------------------------------------------

    private String adaptSqlForDialect(String sql) {
        if (sql == null || dialect != Dialect.MYSQL) {
            return sql;
        }

        String adapted = sql;

        // 1. AUTOINCREMENT
        adapted = adapted.replaceAll("(?i)INTEGER\\s+PRIMARY\\s+KEY\\s+AUTOINCREMENT",
                "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        adapted = adapted.replaceAll("(?i)INT\\s+PRIMARY\\s+KEY\\s+AUTOINCREMENT",
                "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");

        // 2. Index syntax
        adapted = adapted.replaceAll("(?i)CREATE\\s+INDEX\\s+IF\\s+NOT\\s+EXISTS", "CREATE INDEX");

        // 3. SQLite-only clauses — just remove them
        adapted = adapted.replaceAll("(?i)COLLATE\\s+NOCASE", "");
        adapted = adapted.replaceAll("(?i)WITHOUT\\s+ROWID", "");

        // 4. BOOLEAN → TINYINT(1)
        adapted = adapted.replaceAll("(?i)\\bBOOLEAN\\b", "TINYINT(1)");

        // 5. INSERT OR IGNORE / INSERT OR REPLACE  (SQLite-only)
        adapted = adapted.replaceAll("(?i)^\\s*INSERT\\s+OR\\s+IGNORE\\s+", "INSERT IGNORE ");
        adapted = adapted.replaceAll("(?i)^\\s*INSERT\\s+OR\\s+REPLACE\\s+", "REPLACE ");

        // 6. TEXT columns that are used as keys must become VARCHAR
        adapted = rewriteTextKeysForMySql(adapted);

        // 7. ON CONFLICT → INSERT IGNORE / ON DUPLICATE KEY UPDATE
        //    This is the BULLETPROOF version — catches any formatting.
        adapted = rewriteOnConflictForMySql(adapted);

        return adapted.trim();
    }

    /**
     * MySQL cannot use TEXT as PRIMARY KEY or in a UNIQUE constraint without a prefix length.
     * This replaces TEXT with VARCHAR(191) for columns that appear to be key candidates
     * (uuid, id, name, key, etc.) based on column name heuristics.
     *
     * Only rewrites TEXT that appears as a column definition inside CREATE TABLE.
     */
    private String rewriteTextKeysForMySql(String sql) {
        // Only act inside CREATE TABLE blocks
        if (!sql.trim().toUpperCase().startsWith("CREATE TABLE")) {
            return sql;
        }

        // Line-by-line: if a line defines a column with TEXT and the column name
        // looks like a key column, replace TEXT with an appropriate VARCHAR.
        String[] lines = sql.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(rewriteTextColumnLine(line)).append("\n");
        }
        return sb.toString();
    }

    private String rewriteTextColumnLine(String line) {
        // Match:  <optional-whitespace> <column-name> TEXT <optional-constraints>
        Pattern p = Pattern.compile("^(\\s*)([`\"]?[A-Za-z0-9_]+[`\"]?)(\\s+)TEXT(\\b.*)?$",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(line);
        if (!m.matches()) {
            return line;
        }
        String indent = m.group(1);
        String colRaw = m.group(2);
        String gap = m.group(3);
        String rest = m.group(4) == null ? "" : m.group(4);

        String colName = colRaw.replace("`", "").replace("\"", "").toLowerCase();
        String newType = inferMySqlType(colName, rest);
        return indent + colRaw + gap + newType + rest;
    }

    private String inferMySqlType(String colName, String restOfLine) {
        String restLower = restOfLine == null ? "" : restOfLine.toLowerCase();

        // If column is used as a primary key or unique constraint line, must be VARCHAR
        boolean isKeyCol = restLower.contains("primary key")
                || restLower.contains("unique")
                || colName.contains("uuid")
                || colName.equals("id")
                || colName.endsWith("_id")
                || colName.endsWith("_uuid");

        if (isKeyCol) {
            if (colName.contains("uuid")) return "VARCHAR(36)";
            return "VARCHAR(191)";
        }

        // Long-form content — keep as TEXT
        if (colName.contains("json")
                || colName.contains("payload")
                || colName.contains("data")
                || colName.contains("description")
                || colName.contains("notes")
                || colName.contains("signature")
                || colName.contains("csv")
                || colName.contains("base64")
                || colName.contains("hash")
                || colName.contains("salt")) {
            return "TEXT";
        }

        // Short-string columns → VARCHAR
        if (colName.contains("name")
                || colName.contains("key")
                || colName.contains("world")
                || colName.contains("type")
                || colName.contains("role")
                || colName.contains("status")
                || colName.contains("tag")
                || colName.contains("material")
                || colName.contains("state")
                || colName.contains("group")
                || colName.contains("server")
                || colName.contains("host")
                || colName.contains("ip")) {
            return "VARCHAR(191)";
        }

        // Default — keep TEXT (safe for non-key columns)
        return "TEXT";
    }

    /**
     * BULLETPROOF rewrite of SQLite ON CONFLICT into MySQL syntax.
     * Uses simple string search — no regex that can silently miss edge cases.
     */
    private String rewriteOnConflictForMySql(String sql) {
        String trimmed = sql.trim();

        // Normalize whitespace for reliable matching
        String normalized = trimmed.replaceAll("\\s+", " ");
        String normalizedLower = normalized.toLowerCase();

        // Find "ON CONFLICT" anywhere in the SQL
        int conflictIdx = normalizedLower.indexOf(" on conflict");
        if (conflictIdx < 0) {
            return trimmed;
        }

        // Find the closing paren after ON CONFLICT(...)
        int parenOpen = normalizedLower.indexOf("(", conflictIdx);
        if (parenOpen < 0) return trimmed;
        int parenClose = normalizedLower.indexOf(")", parenOpen);
        if (parenClose < 0) return trimmed;

        // What comes after the closing paren?
        String afterParen = normalizedLower.substring(parenClose + 1).trim();

        if (afterParen.startsWith("do nothing")) {
            // ON CONFLICT(...) DO NOTHING  →  INSERT IGNORE (remove ON CONFLICT part)
            String beforeConflict = normalized.substring(0, conflictIdx);
            // Replace "INSERT" with "INSERT IGNORE" in the part before ON CONFLICT
            int insertPos = beforeConflict.toLowerCase().indexOf("insert");
            if (insertPos < 0) return trimmed;
            return beforeConflict.substring(0, insertPos)
                    + "INSERT IGNORE"
                    + beforeConflict.substring(insertPos + "INSERT".length());

        } else if (afterParen.startsWith("do update set ") || afterParen.startsWith("do update set\n")) {
            // ON CONFLICT(...) DO UPDATE SET ...  →  ON DUPLICATE KEY UPDATE ...
            String beforeConflict = normalized.substring(0, conflictIdx);

            // Find where "SET " starts after DO UPDATE
            int setKeyword = normalizedLower.indexOf(" set ", parenClose);
            if (setKeyword < 0) return trimmed;
            String setPart = normalized.substring(setKeyword + " set ".length()).trim();

            // Replace excluded.col → VALUES(col)
            setPart = setPart.replaceAll("(?i)\\bexcluded\\.([A-Za-z0-9_]+)", "VALUES($1)");

            // Replace table.col + VALUES(col) → col + VALUES(col)
            setPart = setPart.replaceAll(
                    "(?i)\\b[A-Za-z0-9_]+\\.([A-Za-z0-9_]+)(\\s*\\+\\s*VALUES\\([^)]+\\))", "$1$2");

            return beforeConflict + " ON DUPLICATE KEY UPDATE " + setPart;
        }

        // Unknown pattern after ON CONFLICT — return as-is (will likely fail, but at least logged)
        logger.warning("adaptSqlForDialect: unknown ON CONFLICT pattern, passing through: "
                + normalizedLower.substring(conflictIdx, Math.min(conflictIdx + 80, normalizedLower.length())));
        return trimmed;
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private boolean isDuplicateColumn(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("duplicate column")
                || m.contains("duplicate column name")
                || m.contains("duplicate key name")
                || m.contains("already exists");
    }

    private boolean isSqliteConnection(Connection connection) {
        try {
            String url = connection.getMetaData().getURL();
            return url != null && url.startsWith("jdbc:sqlite:");
        } catch (Exception ignored) {
            return false;
        }
    }

    /** @return [table, column] for ALTER TABLE … ADD COLUMN …, or null */
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
        String col = colPart.split(" ")[0].trim()
                .replace("`", "").replace("\"", "");
        tablePart = tablePart.replace("`", "").replace("\"", "");
        return new String[]{tablePart, col};
    }

    private String stripLeadingLineComments(String sql) {
        String[] lines = sql.split("\\R");
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (String line : lines) {
            String t = line.trim();
            if (!started && (t.isEmpty() || t.startsWith("--"))) continue;
            started = true;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private boolean columnExists(Connection connection, String table, String column) {
        try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && name.equalsIgnoreCase(column)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isApplied(Connection connection, int version) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT version FROM fx_schema_history WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> readSqlStatements(String resourcePath) throws Exception {
        InputStream inputStream = plugin.getResource(resourcePath);
        if (inputStream == null) {
            throw new IllegalStateException("Migration resource not found: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().map(line -> {
                String t = line.trim();
                if (t.startsWith("--")) return "";
                int idx = line.indexOf("--");
                return idx >= 0 ? line.substring(0, idx) : line;
            }).reduce("", (a, b) -> a + "\n" + b);

            return java.util.Arrays.stream(content.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }

    // -------------------------------------------------------------------------
    //  DatabaseGateway interface
    // -------------------------------------------------------------------------

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
    public PreparedStatement prepare(Connection connection, String sql,
                                     Object... parameters) throws java.sql.SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
        return statement;
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }
}
