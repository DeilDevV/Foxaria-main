package com.foxaria.admin.wipe;

import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.DatabaseGateway;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Очистка игровых данных сезона.
 *
 * Правила безопасности:
 *  - донат (токены, купленные ранги, подписки) и наказания не затрагиваются;
 *  - каждая кнопка требует подтверждения вторым кликом;
 *  - после выполнения включается кулдаун, чтобы не нажали дважды сгоряча;
 *  - история («что и когда очищено») пишется на диск и переживает рестарт.
 */
public final class WipeService {

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final DatabaseGateway database;
    private final ConfigService configs;

    private final Map<WipeTarget, WipeRecord> history = new EnumMap<>(WipeTarget.class);
    private final ConcurrentMap<UUID, PendingConfirm> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<WipeTarget, Long> runningUntil = new ConcurrentHashMap<>();

    public WipeService(JavaPlugin plugin, DatabaseGateway database, ConfigService configs) {
        this.plugin = plugin;
        this.database = database;
        this.configs = configs;
        loadHistory();
    }

    /** Кулдаун между запусками одной и той же очистки. */
    public long cooldownSeconds() {
        return Math.max(10L, configs.main().getLong("wipe.cooldown-seconds", 60L));
    }

    /** Сколько секунд действует запрос подтверждения. */
    public long confirmWindowSeconds() {
        return Math.max(3L, configs.main().getLong("wipe.confirm-seconds", 10L));
    }

    public WipeRecord lastRun(WipeTarget target) {
        return history.get(target);
    }

    public String lastRunLabel(WipeTarget target) {
        WipeRecord record = history.get(target);
        if (record == null) {
            return "&8ещё не очищалось";
        }
        return "&7" + STAMP.format(Instant.ofEpochMilli(record.timestamp()))
            + " &8· &7" + record.actorName()
            + " &8(&f" + record.affectedRows() + "&8)";
    }

    /** Сколько секунд осталось до повторного запуска. 0 — можно запускать. */
    public long cooldownLeft(WipeTarget target) {
        WipeRecord record = history.get(target);
        if (record == null) {
            return 0L;
        }
        long passed = (System.currentTimeMillis() - record.timestamp()) / 1000L;
        return Math.max(0L, cooldownSeconds() - passed);
    }

    public boolean isRunning(WipeTarget target) {
        Long until = runningUntil.get(target);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Первый клик — запрос подтверждения, второй в течение окна — запуск.
     * Возвращает true, если клик был подтверждением и очистка стартовала.
     */
    public boolean confirmOrRequest(UUID actor, WipeTarget target) {
        PendingConfirm confirm = pending.get(actor);
        long now = System.currentTimeMillis();
        if (confirm != null
            && confirm.target() == target
            && confirm.expiresAt() > now) {
            pending.remove(actor);
            return true;
        }
        pending.put(actor, new PendingConfirm(target, now + confirmWindowSeconds() * 1000L));
        return false;
    }

    public void cancelConfirm(UUID actor) {
        pending.remove(actor);
    }

    public boolean awaitingConfirm(UUID actor, WipeTarget target) {
        PendingConfirm confirm = pending.get(actor);
        return confirm != null && confirm.target() == target && confirm.expiresAt() > System.currentTimeMillis();
    }

    /**
     * Выполняет очистку. Работает на пуле БД, не на главном потоке.
     * Возвращает число затронутых строк.
     */
    public CompletableFuture<Integer> execute(WipeTarget target, UUID actorUuid, String actorName) {
        if (isRunning(target)) {
            return CompletableFuture.completedFuture(-1);
        }
        // Защита от параллельного запуска той же очистки.
        runningUntil.put(target, System.currentTimeMillis() + 60_000L);

        return database.query(connection -> {
            int affected = 0;
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (target.isMoneyReset()) {
                    // ВАЖНО: только balance. tokens — донатная валюта, её не трогаем.
                    try (Statement st = connection.createStatement()) {
                        affected += st.executeUpdate(
                            "UPDATE fx_economy_accounts SET balance = 0 WHERE balance <> 0");
                    }
                    try (Statement st = connection.createStatement()) {
                        st.executeUpdate("DELETE FROM fx_economy_transactions");
                    }
                } else {
                    for (String table : target.tables()) {
                        // Имена таблиц зашиты в enum — подстановки извне нет.
                        try (Statement st = connection.createStatement()) {
                            affected += st.executeUpdate("DELETE FROM " + table);
                        } catch (Exception tableError) {
                            // Таблицы может не быть (модуль отключён) — это не повод
                            // ронять весь вайп, просто пишем в лог и идём дальше.
                            plugin.getLogger().warning("[wipe] " + table + ": " + tableError.getMessage());
                        }
                    }
                }
                connection.commit();
                return affected;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }).whenComplete((affected, error) -> {
            runningUntil.remove(target);
            if (error == null && affected != null && affected >= 0) {
                WipeRecord record = new WipeRecord(System.currentTimeMillis(), actorUuid, actorName, affected);
                history.put(target, record);
                saveHistoryAsync();
            }
        });
    }

    public String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(Math.max(0L, seconds));
        if (duration.toMinutes() > 0) {
            return duration.toMinutes() + " мин " + (duration.toSecondsPart()) + " сек";
        }
        return duration.getSeconds() + " сек";
    }

    // ── История на диске ──────────────────────────────────────────────

    private File historyFile() {
        return new File(plugin.getDataFolder(), "wipe-history.yml");
    }

    private void loadHistory() {
        File file = historyFile();
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (WipeTarget target : WipeTarget.values()) {
            String key = target.name().toLowerCase(Locale.ROOT);
            if (!yaml.contains(key + ".timestamp")) {
                continue;
            }
            history.put(target, new WipeRecord(
                yaml.getLong(key + ".timestamp"),
                parseUuid(yaml.getString(key + ".actor-uuid")),
                yaml.getString(key + ".actor-name", "—"),
                yaml.getInt(key + ".rows")
            ));
        }
    }

    private void saveHistoryAsync() {
        Map<WipeTarget, WipeRecord> snapshot = new EnumMap<>(history);
        Runnable task = () -> {
            FileConfiguration yaml = new YamlConfiguration();
            snapshot.forEach((target, record) -> {
                String key = target.name().toLowerCase(Locale.ROOT);
                yaml.set(key + ".timestamp", record.timestamp());
                yaml.set(key + ".actor-uuid", record.actorUuid() == null ? null : record.actorUuid().toString());
                yaml.set(key + ".actor-name", record.actorName());
                yaml.set(key + ".rows", record.affectedRows());
            });
            try {
                plugin.getDataFolder().mkdirs();
                yaml.save(historyFile());
            } catch (Exception error) {
                plugin.getLogger().warning("[wipe] history save failed: " + error.getMessage());
            }
        };
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record WipeRecord(long timestamp, UUID actorUuid, String actorName, int affectedRows) {
    }

    private record PendingConfirm(WipeTarget target, long expiresAt) {
    }
}
