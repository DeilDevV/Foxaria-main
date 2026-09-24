package com.foxaria.guilds;

import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.foxaria.guilds.GuildModels.GuildChestSnapshot;
import static com.foxaria.guilds.GuildModels.GuildMemberRecord;
import static com.foxaria.guilds.GuildModels.GuildRecord;

public final class GuildRepository {

    private final DatabaseGateway database;

    public GuildRepository(DatabaseGateway database) {
        this.database = database;
    }

    public CompletableFuture<Optional<GuildRecord>> byPlayer(UUID playerUuid) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                SELECT g.id, g.name, g.owner_uuid, g.created_at, g.bank_balance, g.guild_coins, g.guild_points, g.level, g.chest_rows, g.shop_tier, g.member_slots_bonus
                     , g.motd, g.friendly_fire, g.tag_color
                FROM fx_guild_members gm
                JOIN fx_guilds g ON g.id = gm.guild_id
                WHERE gm.player_uuid = ?
                """, playerUuid.toString());
                 var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readGuild(rs));
            }
        });
    }

    public CompletableFuture<Optional<GuildRecord>> byName(String name) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                SELECT id, name, owner_uuid, created_at, bank_balance, guild_coins, guild_points, level, chest_rows, shop_tier, member_slots_bonus, motd, friendly_fire, tag_color
                FROM fx_guilds WHERE lower(name)=lower(?)
                """, name);
                 var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readGuild(rs));
            }
        });
    }

    public CompletableFuture<Optional<GuildRecord>> byId(String guildId) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                SELECT id, name, owner_uuid, created_at, bank_balance, guild_coins, guild_points, level, chest_rows, shop_tier, member_slots_bonus, motd, friendly_fire, tag_color
                FROM fx_guilds WHERE id=?
                """, guildId);
                 var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readGuild(rs));
            }
        });
    }

    public CompletableFuture<List<GuildRecord>> listGuilds() {
        return database.query(connection -> {
            List<GuildRecord> guilds = new ArrayList<>();
            try (var statement = database.prepare(connection, """
                SELECT id, name, owner_uuid, created_at, bank_balance, guild_coins, guild_points, level, chest_rows, shop_tier, member_slots_bonus, motd, friendly_fire, tag_color
                FROM fx_guilds
                ORDER BY name ASC
                """);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    guilds.add(readGuild(rs));
                }
            }
            return guilds;
        });
    }

    public CompletableFuture<Boolean> createGuild(GuildRecord guild, GuildMemberRecord ownerMember) {
        return database.query(connection -> {
            connection.setAutoCommit(false);
            try {
                try (var insertGuild = database.prepare(connection, """
                    INSERT INTO fx_guilds (id, name, owner_uuid, created_at, bank_balance, guild_coins, guild_points, level, chest_rows, shop_tier, member_slots_bonus, motd, friendly_fire, tag_color)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    guild.id(), guild.name(), guild.ownerUuid().toString(), guild.createdAt(), guild.bankBalance().toPlainString(),
                    guild.guildCoins(), guild.guildPoints(), guild.level(), guild.chestRows(), guild.shopTier(), guild.memberSlotsBonus(),
                    guild.motd(), guild.friendlyFire() ? 1 : 0, guild.tagColor())) {
                    insertGuild.executeUpdate();
                }
                try (var insertOwner = database.prepare(connection, """
                    INSERT INTO fx_guild_members (guild_id, player_uuid, player_name, role, joined_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    ownerMember.guildId(), ownerMember.playerUuid().toString(), ownerMember.playerName(), ownerMember.role(), ownerMember.joinedAt())) {
                    insertOwner.executeUpdate();
                }
                try (var insertStats = database.prepare(connection, """
                    INSERT INTO fx_guild_stats (guild_id, total_kills)
                    VALUES (?, 0)
                    """ + (database.isMySql()
                        ? "ON DUPLICATE KEY UPDATE guild_id = guild_id\n"
                        : "ON CONFLICT(guild_id) DO NOTHING\n"),
                    guild.id())) {
                    insertStats.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> upsertRole(String guildId, String roleId, String displayName, int weight, Set<String> flags) {
        return database.execute(connection -> {
            String flagData = String.join(",", flags);
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_guild_roles (guild_id, role_id, display_name, weight, flags)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    display_name=VALUES(display_name),
                    weight=VALUES(weight),
                    flags=VALUES(flags)
                """
                : """
                INSERT INTO fx_guild_roles (guild_id, role_id, display_name, weight, flags)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(guild_id, role_id) DO UPDATE SET
                    display_name=excluded.display_name,
                    weight=excluded.weight,
                    flags=excluded.flags
                """;
            try (var statement = database.prepare(connection, sql, guildId, roleId, displayName, weight, flagData)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<String>> memberRole(String guildId, UUID playerUuid) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                SELECT role FROM fx_guild_members WHERE guild_id=? AND player_uuid=?
                """, guildId, playerUuid.toString());
                 var rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.ofNullable(rs.getString("role"));
            }
        });
    }

    public CompletableFuture<Set<String>> roleFlags(String guildId, String roleId) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                SELECT flags FROM fx_guild_roles WHERE guild_id=? AND role_id=?
                """, guildId, roleId);
                 var rs = statement.executeQuery()) {
                if (!rs.next()) return Set.of();
                String raw = rs.getString("flags");
                if (raw == null || raw.isBlank()) return Set.of();
                return new HashSet<>(Arrays.asList(raw.split(",")));
            }
        });
    }

    public CompletableFuture<Map<String, Set<String>>> allRoleFlags(String guildId) {
        return database.query(connection -> {
            Map<String, Set<String>> map = new HashMap<>();
            try (var statement = database.prepare(connection, """
                SELECT role_id, flags FROM fx_guild_roles WHERE guild_id=?
                """, guildId);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    String roleId = rs.getString("role_id");
                    String raw = rs.getString("flags");
                    Set<String> set = raw == null || raw.isBlank() ? new HashSet<>() : new HashSet<>(Arrays.asList(raw.split(",")));
                    map.put(roleId, set);
                }
            }
            return map;
        });
    }

    public CompletableFuture<List<GuildMemberRecord>> members(String guildId) {
        return database.query(connection -> {
            List<GuildMemberRecord> members = new ArrayList<>();
            try (var statement = database.prepare(connection, """
                SELECT guild_id, player_uuid, player_name, role, joined_at
                FROM fx_guild_members
                WHERE guild_id = ?
                ORDER BY role DESC, joined_at ASC
                """, guildId);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    members.add(new GuildMemberRecord(
                        rs.getString("guild_id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getString("role"),
                        rs.getLong("joined_at")
                    ));
                }
            }
            return members;
        });
    }

    public CompletableFuture<Void> addKillAndRewards(String guildId, long coins, long points) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, """
                UPDATE fx_guilds
                SET guild_coins = guild_coins + ?, guild_points = guild_points + ?
                WHERE id = ?
                """, coins, points, guildId)) {
                statement.executeUpdate();
            }
            try (var statement = database.prepare(connection, """
                INSERT INTO fx_guild_stats (guild_id, total_kills)
                VALUES (?, 1)
                """ + (database.isMySql()
                    ? "ON DUPLICATE KEY UPDATE total_kills = total_kills + VALUES(total_kills)\n"
                    : "ON CONFLICT(guild_id) DO UPDATE SET total_kills = total_kills + 1\n"),
                guildId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> addCoinsAndPoints(String guildId, long coins, long points) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, """
                UPDATE fx_guilds
                SET guild_coins = guild_coins + ?, guild_points = guild_points + ?
                WHERE id = ?
                """, coins, points, guildId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Long> totalKills(String guildId) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection,
                "SELECT total_kills FROM fx_guild_stats WHERE guild_id = ?", guildId);
                 var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return rs.getLong("total_kills");
            }
        });
    }

    public CompletableFuture<Integer> countMembers(String guildId) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection,
                "SELECT COUNT(*) FROM fx_guild_members WHERE guild_id = ?", guildId);
                 var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt(1);
            }
        });
    }

    /**
     * Победы в guild war: записи активности с победившей гильдией в details (окончание ":guildId").
     */
    public CompletableFuture<Integer> countWarWins(String guildId) {
        return database.query(connection -> {
            String suffix = ":" + guildId;
            try (var statement = database.prepare(connection, """
                SELECT COUNT(*) FROM fx_guild_activity
                WHERE guild_id = ? AND action_key = 'WAR_END' AND details LIKE ?
                """, guildId, "%" + suffix);
                 var rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt(1);
            }
        });
    }

    public CompletableFuture<Boolean> changeBank(String guildId, BigDecimal delta) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                UPDATE fx_guilds
                SET bank_balance = bank_balance + ?
                WHERE id = ? AND bank_balance + ? >= 0
                """, delta.toPlainString(), guildId, delta.toPlainString())) {
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> spendCoins(String guildId, long amount) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                UPDATE fx_guilds
                SET guild_coins = guild_coins - ?
                WHERE id = ? AND guild_coins >= ?
                """, amount, guildId, amount)) {
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> spendPoints(String guildId, long amount) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection, """
                UPDATE fx_guilds
                SET guild_points = guild_points - ?
                WHERE id = ? AND guild_points >= ?
                """, amount, guildId, amount)) {
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Void> setLevel(String guildId, int level) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, "UPDATE fx_guilds SET level=? WHERE id=?", level, guildId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> applyUpgrade(String guildId, String key, int nextLevel) {
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_guild_upgrades (guild_id, upgrade_key, level)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE level=VALUES(level)
                """
                : """
                INSERT INTO fx_guild_upgrades (guild_id, upgrade_key, level)
                VALUES (?, ?, ?)
                ON CONFLICT(guild_id, upgrade_key) DO UPDATE SET level=excluded.level
                """;
            try (var statement = database.prepare(connection, sql, guildId, key, nextLevel)) {
                statement.executeUpdate();
            }
            String column = switch (key) {
                case "chest_size" -> "chest_rows";
                case "shop_tier" -> "shop_tier";
                case "member_slots" -> "member_slots_bonus";
                default -> null;
            };
            if (column != null) {
                int value = switch (key) {
                    case "chest_size" -> 3 + nextLevel;
                    case "shop_tier" -> 1 + nextLevel;
                    case "member_slots" -> nextLevel * 2;
                    default -> 0;
                };
                try (var update = database.prepare(connection, "UPDATE fx_guilds SET " + column + " = ? WHERE id = ?", value, guildId)) {
                    update.executeUpdate();
                }
            }
        });
    }

    public CompletableFuture<Map<String, Integer>> upgrades(String guildId) {
        return database.query(connection -> {
            Map<String, Integer> result = new HashMap<>();
            try (var statement = database.prepare(connection, """
                SELECT upgrade_key, level FROM fx_guild_upgrades WHERE guild_id = ?
                """, guildId);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("upgrade_key"), rs.getInt("level"));
                }
            }
            return result;
        });
    }

    public CompletableFuture<Void> setFriendlyFire(String guildId, boolean enabled) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, "UPDATE fx_guilds SET friendly_fire = ? WHERE id = ?", enabled ? 1 : 0, guildId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> setMotd(String guildId, String motd) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, "UPDATE fx_guilds SET motd = ? WHERE id = ?", motd, guildId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> setTagColor(String guildId, String colorId) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, "UPDATE fx_guilds SET tag_color = ? WHERE id = ?", colorId, guildId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> appendActivity(String guildId, String actorName, String actionKey, String details) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, """
                INSERT INTO fx_guild_activity (guild_id, actor_name, action_key, details, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, guildId, actorName, actionKey, details, System.currentTimeMillis())) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<String>> latestActivity(String guildId, int limit) {
        return database.query(connection -> {
            List<String> lines = new ArrayList<>();
            try (var statement = database.prepare(connection, """
                SELECT actor_name, action_key, details, created_at
                FROM fx_guild_activity
                WHERE guild_id = ?
                ORDER BY id DESC
                LIMIT ?
                """, guildId, limit);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("action_key");
                    String det = rs.getString("details");
                    lines.add("[" + activityActionRu(key) + "] " + rs.getString("actor_name") + ": " + activityDetailsRu(key, det));
                }
            }
            return lines;
        });
    }

    public CompletableFuture<List<String>> latestWarActivity(String guildId, int limit) {
        return database.query(connection -> {
            List<String> lines = new ArrayList<>();
            try (var statement = database.prepare(connection, """
                SELECT actor_name, action_key, details, created_at
                FROM fx_guild_activity
                WHERE guild_id = ? AND action_key LIKE 'WAR_%'
                ORDER BY id DESC
                LIMIT ?
                """, guildId, limit);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("action_key");
                    String det = rs.getString("details");
                    lines.add("[" + activityActionRu(key) + "] " + rs.getString("actor_name") + ": " + activityDetailsRu(key, det));
                }
            }
            return lines;
        });
    }

    private static String activityActionRu(String actionKey) {
        if (actionKey == null) {
            return "";
        }
        return switch (actionKey) {
            case "MOTD" -> "Слоган";
            case "FRIENDLY_FIRE" -> "Друж. огонь";
            case "BANK_DEPOSIT" -> "Взнос в банк";
            case "BANK_WITHDRAW" -> "Снятие с банка";
            case "UPGRADE" -> "Улучшение";
            case "SHOP_BUY" -> "Покупка";
            case "WAR_QUEUE_JOIN" -> "Очередь войны";
            case "WAR_INVITE_SENT" -> "Приглашение на войну";
            case "WAR_START" -> "Война (старт)";
            case "WAR_END" -> "Война (конец)";
            default -> actionKey;
        };
    }

    private static String activityDetailsRu(String actionKey, String details) {
        if (details == null) {
            return "";
        }
        if ("FRIENDLY_FIRE".equals(actionKey)) {
            return details.replace("ON", "включён").replace("OFF", "выключен");
        }
        return details;
    }

    public CompletableFuture<Boolean> isLevelRewardClaimed(String guildId, int level) {
        return database.query(connection -> {
            try (var statement = database.prepare(connection,
                "SELECT level FROM fx_guild_level_rewards_claimed WHERE guild_id=? AND level=?", guildId, level);
                 var rs = statement.executeQuery()) {
                return rs.next();
            }
        });
    }

    /** id квестов (1..21), по которым уже забрана награда — колонка level в таблице = номер квеста. */
    public CompletableFuture<Set<Integer>> claimedGuildQuestIds(String guildId) {
        return database.query(connection -> {
            Set<Integer> ids = new HashSet<>();
            try (var statement = database.prepare(connection,
                "SELECT level FROM fx_guild_level_rewards_claimed WHERE guild_id=?", guildId);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("level"));
                }
            }
            return ids;
        });
    }

    public CompletableFuture<Void> markLevelRewardClaimed(String guildId, int level, UUID by) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, """
                INSERT INTO fx_guild_level_rewards_claimed (guild_id, level, claimed_by_uuid, claimed_at)
                VALUES (?, ?, ?, ?)
                """, guildId, level, by.toString(), System.currentTimeMillis())) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Map<Integer, long[]>> loadAllObjectiveProgress(String guildId) {
        return database.query(connection -> {
            Map<Integer, Map<Integer, Long>> tmp = new HashMap<>();
            try (var statement = database.prepare(connection, """
                SELECT level, objective_index, progress
                FROM fx_guild_level_objective_progress
                WHERE guild_id = ?
                """, guildId);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    int lvl = rs.getInt("level");
                    int idx = rs.getInt("objective_index");
                    long p = rs.getLong("progress");
                    tmp.computeIfAbsent(lvl, k -> new HashMap<>()).put(idx, p);
                }
            }
            Map<Integer, long[]> out = new HashMap<>();
            for (Map.Entry<Integer, Map<Integer, Long>> e : tmp.entrySet()) {
                int maxIdx = e.getValue().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                long[] arr = new long[maxIdx + 1];
                for (Map.Entry<Integer, Long> cell : e.getValue().entrySet()) {
                    if (cell.getKey() >= 0 && cell.getKey() < arr.length) {
                        arr[cell.getKey()] = cell.getValue();
                    }
                }
                out.put(e.getKey(), arr);
            }
            return out;
        });
    }

    public CompletableFuture<Void> addObjectiveProgressDelta(String guildId, int level, int objectiveIndex, long delta) {
        if (delta <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_guild_level_objective_progress (guild_id, level, objective_index, progress)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    progress = progress + VALUES(progress)
                """
                : """
                INSERT INTO fx_guild_level_objective_progress (guild_id, level, objective_index, progress)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(guild_id, level, objective_index) DO UPDATE SET
                    progress = fx_guild_level_objective_progress.progress + excluded.progress
                """;
            try (var statement = database.prepare(connection, sql, guildId, level, objectiveIndex, delta)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> upsertArena(String arenaId, String displayName, String world, String spawnA, String spawnB, boolean enabled) {
        return database.execute(connection -> {
            String sql = database.isMySql()
                ? """
                INSERT INTO fx_guild_war_arenas (arena_id, display_name, world, spawn_a, spawn_b, enabled)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    display_name=VALUES(display_name),
                    world=VALUES(world),
                    spawn_a=VALUES(spawn_a),
                    spawn_b=VALUES(spawn_b),
                    enabled=VALUES(enabled)
                """
                : """
                INSERT INTO fx_guild_war_arenas (arena_id, display_name, world, spawn_a, spawn_b, enabled)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(arena_id) DO UPDATE SET
                    display_name=excluded.display_name,
                    world=excluded.world,
                    spawn_a=excluded.spawn_a,
                    spawn_b=excluded.spawn_b,
                    enabled=excluded.enabled
                """;
            try (var statement = database.prepare(connection, sql, arenaId, displayName, world, spawnA, spawnB, enabled ? 1 : 0)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateArenaSpawn(String arenaId, boolean sideA, String world, String spawn) {
        return database.execute(connection -> {
            String column = sideA ? "spawn_a" : "spawn_b";
            try (var statement = database.prepare(connection,
                "UPDATE fx_guild_war_arenas SET world=?, " + column + "=? WHERE arena_id=?",
                world, spawn, arenaId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> setArenaEnabled(String arenaId, boolean enabled) {
        return database.execute(connection -> {
            try (var statement = database.prepare(connection, "UPDATE fx_guild_war_arenas SET enabled=? WHERE arena_id=?", enabled ? 1 : 0, arenaId)) {
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<WarArenaRecord>> listArenas(boolean onlyEnabled) {
        return database.query(connection -> {
            List<WarArenaRecord> arenas = new ArrayList<>();
            String sql = onlyEnabled
                ? "SELECT arena_id, display_name, world, spawn_a, spawn_b, enabled FROM fx_guild_war_arenas WHERE enabled=1 ORDER BY arena_id"
                : "SELECT arena_id, display_name, world, spawn_a, spawn_b, enabled FROM fx_guild_war_arenas ORDER BY arena_id";
            try (var statement = database.prepare(connection, sql);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    arenas.add(new WarArenaRecord(
                        rs.getString("arena_id"),
                        rs.getString("display_name"),
                        rs.getString("world"),
                        rs.getString("spawn_a"),
                        rs.getString("spawn_b"),
                        rs.getInt("enabled") == 1
                    ));
                }
            }
            return arenas;
        });
    }

    public record WarArenaRecord(String arenaId, String displayName, String world, String spawnA, String spawnB, boolean enabled) {}

    public CompletableFuture<GuildChestSnapshot> chest(String guildId, int defaultRows) {
        return database.query(connection -> {
            int rows = defaultRows;
            try (var rowStatement = database.prepare(connection, """
                SELECT chest_rows FROM fx_guilds WHERE id = ?
                """, guildId);
                 var rowRs = rowStatement.executeQuery()) {
                if (rowRs.next()) {
                    rows = Math.max(1, rowRs.getInt("chest_rows"));
                }
            }
            Map<Integer, ItemStack> items = new HashMap<>();
            try (var statement = database.prepare(connection, """
                SELECT slot, item_data FROM fx_guild_chest_items WHERE guild_id = ?
                """, guildId);
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getInt("slot"), ItemStackSerializer.deserialize(rs.getString("item_data")));
                }
            }
            return new GuildChestSnapshot(guildId, rows, items);
        });
    }

    public CompletableFuture<Void> saveChest(String guildId, Map<Integer, ItemStack> items) {
        return database.execute(connection -> {
            connection.setAutoCommit(false);
            try {
                try (var delete = database.prepare(connection, "DELETE FROM fx_guild_chest_items WHERE guild_id = ?", guildId)) {
                    delete.executeUpdate();
                }
                for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                    try (var insert = database.prepare(connection, """
                        INSERT INTO fx_guild_chest_items (guild_id, slot, item_data)
                        VALUES (?, ?, ?)
                        """, guildId, entry.getKey(), ItemStackSerializer.serialize(entry.getValue()))) {
                        insert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> deleteGuild(String guildId) {
        return database.execute(connection -> {
            connection.setAutoCommit(false);
            try {
                for (String table : new String[]{
                    "fx_guild_chest_items", "fx_guild_members", "fx_guild_roles",
                    "fx_guild_stats", "fx_guild_upgrades", "fx_guild_level_rewards",
                    "fx_guild_objectives", "fx_guild_activity", "fx_guild_war_activity",
                    "fx_guilds"
                }) {
                    try (var stmt = database.prepare(connection,
                            "DELETE FROM " + table + " WHERE guild_id = ?", guildId)) {
                        stmt.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    /** Check if player has a pending site-invite in fx_guild_invites and return guild id. */
    public CompletableFuture<Optional<String>> pendingInvite(UUID playerUuid) {
        return database.query(connection -> {
            try (var stmt = database.prepare(connection, """
                SELECT guild_id FROM fx_guild_invites
                WHERE target_uuid = ? AND expires_at > ?
                ORDER BY expires_at DESC LIMIT 1
                """, playerUuid.toString(), System.currentTimeMillis());
                 var rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getString("guild_id"));
            }
        });
    }

    public CompletableFuture<Void> removeInvite(UUID playerUuid) {
        return database.execute(connection -> {
            try (var stmt = database.prepare(connection,
                    "DELETE FROM fx_guild_invites WHERE target_uuid = ?", playerUuid.toString())) {
                stmt.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> addMember(String guildId, UUID playerUuid, String playerName, String role) {
        return database.execute(connection -> {
            try (var stmt = database.prepare(connection, """
                INSERT INTO fx_guild_members (guild_id, player_uuid, player_name, role, joined_at)
                VALUES (?, ?, ?, ?, ?)
                """, guildId, playerUuid.toString(), playerName, role, System.currentTimeMillis())) {
                stmt.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> removeMember(String guildId, UUID playerUuid) {
        return database.execute(connection -> {
            try (var stmt = database.prepare(connection,
                    "DELETE FROM fx_guild_members WHERE guild_id = ? AND player_uuid = ?",
                    guildId, playerUuid.toString())) {
                stmt.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateMemberRole(String guildId, UUID playerUuid, String newRole) {
        return database.execute(connection -> {
            try (var stmt = database.prepare(connection,
                    "UPDATE fx_guild_members SET role = ? WHERE guild_id = ? AND player_uuid = ?",
                    newRole, guildId, playerUuid.toString())) {
                stmt.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<GuildMemberRecord>> findMemberByName(String playerName) {
        return database.query(connection -> {
            try (var stmt = database.prepare(connection, """
                SELECT guild_id, player_uuid, player_name, role, joined_at
                FROM fx_guild_members WHERE LOWER(player_name) = LOWER(?) LIMIT 1
                """, playerName);
                 var rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new GuildMemberRecord(
                    rs.getString("guild_id"),
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("player_name"),
                    rs.getString("role"),
                    rs.getLong("joined_at")
                ));
            }
        });
    }

    private GuildRecord readGuild(ResultSet rs) throws Exception {
        return new GuildRecord(
            rs.getString("id"),
            rs.getString("name"),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getLong("created_at"),
            new BigDecimal(rs.getString("bank_balance")),
            rs.getLong("guild_coins"),
            rs.getLong("guild_points"),
            rs.getInt("level"),
            rs.getInt("chest_rows"),
            rs.getInt("shop_tier"),
            rs.getInt("member_slots_bonus"),
            rs.getString("motd"),
            rs.getInt("friendly_fire") == 1,
            rs.getString("tag_color")
        );
    }
}
