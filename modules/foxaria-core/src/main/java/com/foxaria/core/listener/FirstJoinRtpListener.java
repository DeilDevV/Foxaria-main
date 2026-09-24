package com.foxaria.core.listener;

import com.foxaria.api.service.ConfigService;
import com.foxaria.core.service.FirstJoinTrackerService;
import com.foxaria.core.service.TeleportService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Стартовый случайный заброс — строго один раз на игрока.
 *
 * Основной путь — {@link FirstJoinSpawnLocationListener}: он ставит точку
 * ещё до входа в мир, из прогретого пула. Этот листенер нужен как запасной,
 * если пул пуст (например, сразу после старта сервера).
 *
 * При обычном перезаходе (игрок не умирал) не делает ничего — игрок остаётся
 * там, где вышел. Новый случайный заброс даёт только respawn после смерти.
 */
public final class FirstJoinRtpListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final TeleportService teleport;
    private final FirstJoinTrackerService firstJoin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public FirstJoinRtpListener(JavaPlugin plugin, ConfigService configs, TeleportService teleport, FirstJoinTrackerService firstJoin) {
        this.plugin = plugin;
        this.configs = configs;
        this.teleport = teleport;
        this.firstJoin = firstJoin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!configs.main().getBoolean("rtp.auto-first-join.enabled", true)) {
            return;
        }

        // Точку уже поставило событие спавна — только показываем титр.
        if (firstJoin.consumeFirstJoinThisLogin(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    showFirstJoinTitle(player);
                }
            }, 1L);
            return;
        }

        // Не первый вход — ничего не делаем, игрок остаётся на своём месте.
        if (!firstJoin.isFirstJoin(player.getUniqueId())) {
            return;
        }

        // Отмечаем СРАЗУ: если игрок отвалится во время поиска точки,
        // повторного случайного заброса при следующем входе не будет.
        firstJoin.markSeen(player.getUniqueId());

        long delay = Math.max(1L, configs.main().getLong("rtp.auto-first-join.delay-ticks", 40L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            showFirstJoinTitle(player);
            teleport.randomTeleport(player, TeleportService.RtpReason.FIRST_JOIN);
        }, delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        firstJoin.clearLoginState(event.getPlayer().getUniqueId());
    }

    private void showFirstJoinTitle(Player player) {
        Duration fadeIn = Duration.ofMillis(Math.max(0L, configs.main().getLong("rtp.titles.fade-in-ms", 300L)));
        Duration stay = Duration.ofMillis(Math.max(0L, configs.main().getLong("rtp.titles.stay-ms", 1400L)));
        Duration fadeOut = Duration.ofMillis(Math.max(0L, configs.main().getLong("rtp.titles.fade-out-ms", 500L)));
        String titleLegacy = configs.main().getString("rtp.titles.first-join.title", "&6FOXARIA");
        String subtitleLegacy = configs.main().getString("rtp.titles.first-join.subtitle", "&eСлучайная точка высадки");
        player.showTitle(Title.title(
            legacy.deserialize(titleLegacy == null ? "" : titleLegacy),
            legacy.deserialize(subtitleLegacy == null ? "" : subtitleLegacy),
            Title.Times.times(fadeIn, stay, fadeOut)
        ));
    }
}
