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
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Первый вход на сервер: автоматический RTP (только один раз на этом backend'е).
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

        // If spawn-location listener already handled first-join, just show the title and exit.
        if (firstJoin.consumeFirstJoinThisLogin(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                showFirstJoinTitle(player);
            }, 1L);
            return;
        }

        // We only do the join-teleport fallback if the tracker says it's first join for this wipe.
        // The actual preferred path is spawn-location event.
        if (!firstJoin.isFirstJoin(player.getUniqueId())) {
            return;
        }
        firstJoin.markFirstJoinThisLogin(player.getUniqueId());
        // Делаем чуть позже, чтобы мир/пермишены/плагины успели прогрузиться.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                showFirstJoinTitle(player);
                teleport.randomTeleport(player, TeleportService.RtpReason.FIRST_JOIN);
                // Mark as seen now (worst case: player logs out mid-flight; next join won't RTP again).
                firstJoin.markSeen(player.getUniqueId());
            }
        }, Math.max(1L, configs.main().getLong("rtp.auto-first-join.delay-ticks", 40L)));
    }

    private void showFirstJoinTitle(Player player) {
        Duration fadeIn = Duration.ofMillis(Math.max(0L, configs.main().getLong("rtp.titles.fade-in-ms", 300L)));
        Duration stay = Duration.ofMillis(Math.max(0L, configs.main().getLong("rtp.titles.stay-ms", 1400L)));
        Duration fadeOut = Duration.ofMillis(Math.max(0L, configs.main().getLong("rtp.titles.fade-out-ms", 500L)));
        String titleLegacy = configs.main().getString("rtp.titles.first-join.title", "&6FOXARIA");
        String subtitleLegacy = configs.main().getString("rtp.titles.first-join.subtitle", "&eРандомная точка спавна");
        player.showTitle(Title.title(
            legacy.deserialize(titleLegacy == null ? "" : titleLegacy),
            legacy.deserialize(subtitleLegacy == null ? "" : subtitleLegacy),
            Title.Times.times(fadeIn, stay, fadeOut)
        ));
    }
}

