package com.foxaria.core.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единый скорборд на игрока: позволяет одновременно держать и сайдбар, и team-неймтеги.
 *
 * Важно: если каждый refresh создавать новый scoreboard, team'ы «сбрасываются» и неймтеги не работают стабильно.
 */
public final class PlayerScoreboardService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public PlayerScoreboardService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(manager.getMainScoreboard());
            }
        }
        boards.clear();
    }

    public Scoreboard scoreboard(Player player) {
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is not available.");
        }
        return boards.computeIfAbsent(player.getUniqueId(), id -> manager.getNewScoreboard());
    }

    public void detach(Player player) {
        boards.remove(player.getUniqueId());
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Создаём scoreboard заранее, чтобы другие сервисы могли сразу добавлять team'ы/объективы.
        scoreboard(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer());
    }
}

