package com.foxaria.retention;

import com.foxaria.api.service.DatabaseGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RetentionRepository {

    private final DatabaseGateway database;

    public RetentionRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<StreakSnapshot> streak(UUID playerUuid) {
        return database.query(connection -> {
            try (PreparedStatement statement = database.prepare(connection, "SELECT * FROM fx_login_streaks WHERE player_uuid = ?", playerUuid.toString());
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new StreakSnapshot(playerUuid, LocalDate.MIN, 0);
                }
                return new StreakSnapshot(
                    playerUuid,
                    LocalDate.parse(resultSet.getString("last_login_date")),
                    resultSet.getInt("streak_days")
                );
            }
        });
    }

    public CompletableFuture<Void> updateStreak(UUID playerUuid, LocalDate lastLoginDate, int streakDays) {
        return database.execute(connection -> {
            try (PreparedStatement update = database.prepare(
                connection,
                "UPDATE fx_login_streaks SET last_login_date = ?, streak_days = ? WHERE player_uuid = ?",
                lastLoginDate.toString(),
                streakDays,
                playerUuid.toString()
            )) {
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_login_streaks (player_uuid, last_login_date, streak_days) VALUES (?, ?, ?)",
                playerUuid.toString(),
                lastLoginDate.toString(),
                streakDays
            )) {
                insert.executeUpdate();
            }
        });
    }

    public CompletableFuture<Map<Long, Boolean>> claimedPlaytimeRewards(UUID playerUuid) {
        return database.query(connection -> {
            Map<Long, Boolean> map = new HashMap<>();
            try (PreparedStatement statement = database.prepare(connection, "SELECT reward_key FROM fx_playtime_rewards WHERE player_uuid = ?", playerUuid.toString());
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    map.put(resultSet.getLong("reward_key"), Boolean.TRUE);
                }
            }
            return map;
        });
    }

    public CompletableFuture<Void> markPlaytimeClaimed(UUID playerUuid, long rewardKey) {
        return database.execute(connection -> {
            try (PreparedStatement statement = database.prepare(
                connection,
                "INSERT INTO fx_playtime_rewards (player_uuid, reward_key, claimed_at) VALUES (?, ?, ?)",
                playerUuid.toString(),
                rewardKey,
                System.currentTimeMillis()
            )) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> claimVote(UUID playerUuid, String siteKey, LocalDate voteDate) {
        return database.query(connection -> {
            try (PreparedStatement read = database.prepare(
                connection,
                "SELECT site_key FROM fx_vote_claims WHERE player_uuid = ? AND site_key = ? AND vote_date = ?",
                playerUuid.toString(),
                siteKey,
                voteDate.toString()
            );
                 ResultSet resultSet = read.executeQuery()) {
                if (resultSet.next()) {
                    return false;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_vote_claims (player_uuid, site_key, vote_date, claimed_at) VALUES (?, ?, ?, ?)",
                playerUuid.toString(),
                siteKey,
                voteDate.toString(),
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
                return true;
            }
        });
    }

    public CompletableFuture<Boolean> createReferral(UUID referrerUuid, UUID referredUuid) {
        return database.query(connection -> {
            try (PreparedStatement read = database.prepare(
                connection,
                "SELECT referred_uuid FROM fx_referrals WHERE referred_uuid = ?",
                referredUuid.toString()
            );
                 ResultSet resultSet = read.executeQuery()) {
                if (resultSet.next()) {
                    return false;
                }
            }
            try (PreparedStatement insert = database.prepare(
                connection,
                "INSERT INTO fx_referrals (referred_uuid, referrer_uuid, created_at) VALUES (?, ?, ?)",
                referredUuid.toString(),
                referrerUuid.toString(),
                System.currentTimeMillis()
            )) {
                insert.executeUpdate();
                return true;
            }
        });
    }

    public record StreakSnapshot(UUID playerUuid, LocalDate lastLoginDate, int streakDays) {
    }
}
