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
 * Tracks "first join" per wipe. Storage is a simple text file with one UUID per line.
 *
 * Wipe = delete the file. This avoids Paper/Spigot {@code hasPlayedBefore()} edge cases on spawn events.
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
                String t = line == null ? "" : line.trim();
                if (t.isEmpty()) continue;
                try {
                    seen.add(UUID.fromString(t));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception ex) {
            logger.warning("Failed to load first-join tracker: " + ex.getMessage());
        }
    }

    public boolean isFirstJoin(UUID uuid) {
        return uuid != null && !seen.contains(uuid);
    }

    /** Mark as seen (persisted). Safe to call multiple times. */
    public void markSeen(UUID uuid) {
        if (uuid == null) return;
        if (!seen.add(uuid)) {
            return;
        }
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                path,
                uuid.toString() + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            logger.warning("Failed to persist first-join tracker: " + ex.getMessage());
        }
    }

    /**
     * Used only for UI (title/guard). This is NOT persisted and is cleared after consume.
     */
    public void markFirstJoinThisLogin(UUID uuid) {
        if (uuid != null) firstJoinThisLogin.add(uuid);
    }

    public boolean consumeFirstJoinThisLogin(UUID uuid) {
        return uuid != null && firstJoinThisLogin.remove(uuid);
    }

    private Path filePath() {
        String file = configs.main().getString("rtp.auto-first-join.tracker-file", "first-join.txt");
        return plugin.getDataFolder().toPath().resolve(file);
    }
}

