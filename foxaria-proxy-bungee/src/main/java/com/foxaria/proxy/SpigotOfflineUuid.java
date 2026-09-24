package com.foxaria.proxy;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Тот же UUID, что {@code Bukkit.getOfflinePlayer(name)} для режима offline/cracked (Spigot).
 * Нужен на прокси, если Mojang вернул другой UUID или API недоступен.
 */
public final class SpigotOfflineUuid {

    private SpigotOfflineUuid() {
    }

    public static UUID fromPlayerName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + name.trim().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }
}
