package com.foxaria.api.service;

import com.foxaria.api.MigrationScript;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DatabaseGateway {

    enum Dialect {
        SQLITE,
        MYSQL,
        POSTGRESQL
    }

    void start();

    void stop();

    void applyMigrations(List<MigrationScript> scripts);

    <T> CompletableFuture<T> query(SqlFunction<Connection, T> work);

    CompletableFuture<Void> execute(SqlConsumer<Connection> work);

    PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws SQLException;

    Dialect dialect();

    default boolean isSqlite() {
        return dialect() == Dialect.SQLITE;
    }

    default boolean isMySql() {
        return dialect() == Dialect.MYSQL;
    }

    default boolean isPostgreSql() {
        return dialect() == Dialect.POSTGRESQL;
    }

    interface SqlFunction<C, T> {
        T apply(C connection) throws Exception;
    }

    interface SqlConsumer<C> {
        void accept(C connection) throws Exception;
    }
}
