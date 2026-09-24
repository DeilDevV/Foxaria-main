package com.foxaria.moderation.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Определение UUID игрока для модерации: онлайн на сервере или Mojang API (если офлайн).
 */
public final class ModerationTargetResolver {

    private ModerationTargetResolver() {
    }

    public static Player findOnlineByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Колбэк выполняется в основном потоке сервера.
     */
    public static void resolveUuid(JavaPlugin plugin, String name, Consumer<Optional<UUID>> onMainThread) {
        Player online = findOnlineByName(name);
        if (online != null) {
            Bukkit.getScheduler().runTask(plugin, () -> onMainThread.accept(Optional.of(online.getUniqueId())));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<UUID> resolved = MojangUuidLookup.fromPlayerName(name);
            Bukkit.getScheduler().runTask(plugin, () -> onMainThread.accept(resolved));
        });
    }
}
