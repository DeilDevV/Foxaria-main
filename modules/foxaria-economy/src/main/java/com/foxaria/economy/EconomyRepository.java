package com.foxaria.economy;

import com.foxaria.api.model.BalanceSnapshot;
import com.foxaria.api.service.DatabaseGateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EconomyRepository {

    private final DatabaseGateway database;

    public EconomyRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Void> ensureAccount(UUID playerUuid) {
        return database.execute(connection -> {
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_economy_accounts SET updated_at = ? WHERE player_uuid = ?",
                System.currentTimeMillis(),
                playerUuid.toString()
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_economy_accounts (player_uuid, balance, tokens, updated_at) VALUES (?, ?, ?, ?)",
                playerUuid.toString(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                0L,
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<BalanceSnapshot> balance(UUID playerUuid) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_economy_accounts WHERE player_uuid = ?",
                playerUuid.toString()
            );
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new BalanceSnapshot(playerUuid, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0L, System.currentTimeMillis());
                }
                return new BalanceSnapshot(
                    playerUuid,
                    resultSet.getBigDecimal("balance").setScale(2, RoundingMode.HALF_UP),
                    resultSet.getLong("tokens"),
                    resultSet.getLong("updated_at")
                );
            }
        });
    }

    public CompletableFuture<Void> deposit(UUID playerUuid, BigDecimal amount, String txId, String reason, UUID actorUuid) {
        BigDecimal normalized = normalize(amount);
        return database.execute(connection -> {
            connection.setAutoCommit(false);
            try {
                ensureAccountSync(connection, playerUuid);
                try (PreparedStatement update = database.prepare(
                    connection,
                    "UPDATE fx_economy_accounts SET balance = balance + ?, updated_at = ? WHERE player_uuid = ?",
                    normalized,
                    System.currentTimeMillis(),
                    playerUuid.toString()
                )) {
                    update.executeUpdate();
                }
                insertTransaction(connection, txId, actorUuid, playerUuid, "DEPOSIT", normalized, BigDecimal.ZERO, reason);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> withdraw(UUID playerUuid, BigDecimal amount, String txId, String reason, UUID actorUuid) {
        BigDecimal normalized = normalize(amount);
        return database.execute(connection -> {
            connection.setAutoCommit(false);
            try {
                ensureAccountSync(connection, playerUuid);
                BigDecimal balance = readBalanceSync(connection, playerUuid);
                if (balance.compareTo(normalized) < 0) {
                    throw new IllegalStateException("Insufficient balance");
                }
                try (PreparedStatement update = database.prepare(
                    connection,
                    "UPDATE fx_economy_accounts SET balance = balance - ?, updated_at = ? WHERE player_uuid = ?",
                    normalized,
                    System.currentTimeMillis(),
                    playerUuid.toString()
                )) {
                    update.executeUpdate();
                }
                insertTransaction(connection, txId, actorUuid, playerUuid, "WITHDRAW", normalized, BigDecimal.ZERO, reason);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> transfer(UUID from, UUID to, BigDecimal amount, BigDecimal fee, String txId, String reason) {
        BigDecimal normalizedAmount = normalize(amount);
        BigDecimal normalizedFee = normalize(fee);
        BigDecimal total = normalizedAmount.add(normalizedFee);
        return database.execute(connection -> {
            connection.setAutoCommit(false);
            try {
                ensureAccountSync(connection, from);
                ensureAccountSync(connection, to);
                BigDecimal balance = readBalanceSync(connection, from);
                if (balance.compareTo(total) < 0) {
                    throw new IllegalStateException("Insufficient balance");
                }
                try (PreparedStatement withdraw = database.prepare(
                    connection,
                    "UPDATE fx_economy_accounts SET balance = balance - ?, updated_at = ? WHERE player_uuid = ?",
                    total,
                    System.currentTimeMillis(),
                    from.toString()
                );
                     PreparedStatement deposit = database.prepare(
                         connection,
                         "UPDATE fx_economy_accounts SET balance = balance + ?, updated_at = ? WHERE player_uuid = ?",
                         normalizedAmount,
                         System.currentTimeMillis(),
                         to.toString()
                     )) {
                    withdraw.executeUpdate();
                    deposit.executeUpdate();
                }
                insertTransaction(connection, txId, from, to, "TRANSFER", normalizedAmount, normalizedFee, reason);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<List<BalanceSnapshot>> top(int limit) {
        return database.query(connection -> {
            List<BalanceSnapshot> results = new ArrayList<>();
            try (PreparedStatement statement = database.prepare(
                connection,
                "SELECT * FROM fx_economy_accounts ORDER BY balance DESC LIMIT ?",
                limit
            );
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(new BalanceSnapshot(
                        UUID.fromString(resultSet.getString("player_uuid")),
                        resultSet.getBigDecimal("balance").setScale(2, RoundingMode.HALF_UP),
                        resultSet.getLong("tokens"),
                        resultSet.getLong("updated_at")
                    ));
                }
            }
            return results;
        });
    }

    private void ensureAccountSync(java.sql.Connection connection, UUID playerUuid) throws Exception {
        try (PreparedStatement update = database.prepare(
            connection,
            "UPDATE fx_economy_accounts SET updated_at = ? WHERE player_uuid = ?",
            System.currentTimeMillis(),
            playerUuid.toString()
        )) {
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = database.prepare(
            connection,
            "INSERT INTO fx_economy_accounts (player_uuid, balance, tokens, updated_at) VALUES (?, ?, ?, ?)",
            playerUuid.toString(),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            0L,
            System.currentTimeMillis()
        )) {
            insert.executeUpdate();
        }
    }

    private BigDecimal readBalanceSync(java.sql.Connection connection, UUID playerUuid) throws Exception {
        try (PreparedStatement statement = database.prepare(
            connection,
            "SELECT balance FROM fx_economy_accounts WHERE player_uuid = ?",
            playerUuid.toString()
        );
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return resultSet.getBigDecimal("balance").setScale(2, RoundingMode.HALF_UP);
        }
    }

    private long readTokensSync(java.sql.Connection connection, UUID playerUuid) throws Exception {
        try (PreparedStatement statement = database.prepare(
            connection,
            "SELECT tokens FROM fx_economy_accounts WHERE player_uuid = ?",
            playerUuid.toString()
        );
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return 0L;
            }
            return resultSet.getLong("tokens");
        }
    }

    public CompletableFuture<Void> addTokens(UUID playerUuid, long amount, String txId, String reason, UUID actorUuid) {
        if (amount <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        return database.execute(connection -> {
            connection.setAutoCommit(false);
            try {
                ensureAccountSync(connection, playerUuid);
                try (PreparedStatement update = database.prepare(
                    connection,
                    "UPDATE fx_economy_accounts SET tokens = tokens + ?, updated_at = ? WHERE player_uuid = ?",
                    amount,
                    System.currentTimeMillis(),
                    playerUuid.toString()
                )) {
                    update.executeUpdate();
                }
                insertTokenTransaction(connection, txId, actorUuid, playerUuid, "TOKEN_DEPOSIT", amount, reason);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> removeTokens(UUID playerUuid, long amount, String txId, String reason, UUID actorUuid) {
        if (amount <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        return database.execute(connection -> {
            connection.setAutoCommit(false);
            try {
                ensureAccountSync(connection, playerUuid);
                long have = readTokensSync(connection, playerUuid);
                if (have < amount) {
                    throw new IllegalStateException("Insufficient tokens");
                }
                try (PreparedStatement update = database.prepare(
                    connection,
                    "UPDATE fx_economy_accounts SET tokens = tokens - ?, updated_at = ? WHERE player_uuid = ?",
                    amount,
                    System.currentTimeMillis(),
                    playerUuid.toString()
                )) {
                    update.executeUpdate();
                }
                insertTokenTransaction(connection, txId, actorUuid, playerUuid, "TOKEN_WITHDRAW", amount, reason);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    private void insertTokenTransaction(
        java.sql.Connection connection,
        String txId,
        UUID actorUuid,
        UUID targetUuid,
        String type,
        long tokenAmount,
        String reason
    ) throws Exception {
        try (PreparedStatement statement = database.prepare(
            connection,
            """
                INSERT INTO fx_economy_transactions
                (id, actor_uuid, target_uuid, type, amount, currency, fee, reason, metadata_json, created_at)
                VALUES (?, ?, ?, ?, ?, 'TOKENS', 0, ?, '{}', ?)
                """,
            txId,
            actorUuid == null ? null : actorUuid.toString(),
            targetUuid == null ? null : targetUuid.toString(),
            type,
            BigDecimal.valueOf(tokenAmount),
            reason,
            System.currentTimeMillis()
        )) {
            statement.executeUpdate();
        }
    }

    private void insertTransaction(
        java.sql.Connection connection,
        String txId,
        UUID actorUuid,
        UUID targetUuid,
        String type,
        BigDecimal amount,
        BigDecimal fee,
        String reason
    ) throws Exception {
        try (PreparedStatement statement = database.prepare(
            connection,
            """
                INSERT INTO fx_economy_transactions
                (id, actor_uuid, target_uuid, type, amount, currency, fee, reason, metadata_json, created_at)
                VALUES (?, ?, ?, ?, ?, 'COINS', ?, ?, '{}', ?)
                """,
            txId,
            actorUuid == null ? null : actorUuid.toString(),
            targetUuid == null ? null : targetUuid.toString(),
            type,
            amount,
            fee,
            reason,
            System.currentTimeMillis()
        )) {
            statement.executeUpdate();
        }
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
