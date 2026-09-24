package com.foxaria.core.service;

import com.foxaria.api.service.ConfigService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Кто уже получал стартовый случайный спавн.
 *
 * Правило сервера: случайный заброс выдаётся ОДИН раз — при первом входе.
 * Дальше игрок при перезаходе остаётся там, где вышел, а новый случайный
 * заброс он получает только после смерти (это делает respawn-листенер).
 *
 * Хранилище — текстовый файл, по одному UUID в строке. Вайп = удалить файл.
 */
public final class FirstJoinTrackerService {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final Logger logger;
    private final Set<UUID> seen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> firstJoinThisLogin = ConcurrentHashMap.newKeySet();

    public FirstJoinTrackerService(JavaPlugin plugin, ConfigService configs, Logger logger) {
        this.plugin = plugin;
        this.configs = configs;
        this.logger = logger;
    }

    public void start() {
        Path path = filePath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    seen.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {
                    // мусорная строка — пропускаем
                }
            }
        } catch (Exception ex) {
            logger.warning("Failed to load first-join tracker: " + ex.getMessage());
        }
    }

    public boolean isFirstJoin(UUID uuid) {
        return uuid != null && !seen.contains(uuid);
    }

    /**
     * Пометить, что стартовый случайный спавн выдан.
     *
     * Вызывать НУЖНО до самой телепортации: раньше отметка ставилась после,
     * и если игрок отваливался в процессе, при следующем входе его
     * забрасывало случайно ещё раз.
     *
     * Запись на диск — асинхронная: метод вызывается в том числе из
     * события логина, а блокирующий I/O там тормозит вход.
     */
    public void markSeen(UUID uuid) {
        if (uuid == null || !seen.add(uuid)) {
            return;
        }
        persistAsync(uuid);
    }

    private void persistAsync(UUID uuid) {
        Runnable write = () -> {
            Path path = filePath();
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(
                    path,
                    uuid + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                );
            } catch (IOException ex) {
                // Не удалось сохранить — вернём игрока в «новички», иначе после
                // рестарта расхождение памяти и файла даст повторный заброс.
                seen.remove(uuid);
                logger.warning("Failed to persist first-join tracker: " + ex.getMessage());
            }
        };
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    /** Только для показа титра на входе. Не сохраняется. */
    public void markFirstJoinThisLogin(UUID uuid) {
        if (uuid != null) {
            firstJoinThisLogin.add(uuid);
        }
    }

    public boolean consumeFirstJoinThisLogin(UUID uuid) {
        return uuid != null && firstJoinThisLogin.remove(uuid);
    }

    /**
     * Сброс временного состояния при выходе.
     * Без этого «хвост» с прошлого входа оставался в памяти и ломал
     * логику показа титра на следующем заходе.
     */
    public void clearLoginState(UUID uuid) {
        if (uuid != null) {
            firstJoinThisLogin.remove(uuid);
        }
    }

    private Path filePath() {
        String file = configs.main().getString("rtp.auto-first-join.tracker-file", "first-join.txt");
        return plugin.getDataFolder().toPath().resolve(file);
    }
}
