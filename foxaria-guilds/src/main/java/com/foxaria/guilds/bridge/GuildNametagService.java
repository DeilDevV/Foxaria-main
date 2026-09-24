package com.foxaria.guilds.bridge;

import com.foxaria.core.service.FoxariaPermissionService;
import com.foxaria.core.service.PlayerScoreboardService;
import com.foxaria.guilds.GuildModels.GuildRecord;
import com.foxaria.guilds.GuildService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Неймтеги над игроком: префикс доната (FoxariaPermissionService, источник Bungee) + ник + тег гильдии.
 *
 * Реализация через scoreboard teams: работает ванильно и обновляется на всех серверах.
 */
public final class GuildNametagService implements Listener {

    private final JavaPlugin plugin;
    private final PlayerScoreboardService scoreboards;
    private final FoxariaPermissionService perms;
    private final GuildService guilds;
    private BukkitTask refreshTask;

    public GuildNametagService(
        JavaPlugin plugin,
        PlayerScoreboardService scoreboards,
        FoxariaPermissionService perms,
        GuildService guilds
    ) {
        this.plugin = plugin;
        this.scoreboards = scoreboards;
        this.perms = perms;
        this.guilds = guilds;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshOnline, 40L, 40L);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refreshPlayerEverywhere(id), 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        String entry = event.getPlayer().getName();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            Team team = board.getTeam(teamId(id));
            if (team != null) {
                team.removeEntry(entry);
            }
        }
    }

    private void refreshOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshPlayerEverywhere(p.getUniqueId());
        }
    }

    private void refreshPlayerEverywhere(UUID playerUuid) {
        Player target = Bukkit.getPlayer(playerUuid);
        if (target == null || !target.isOnline()) {
            return;
        }
        String nameEntry = target.getName();

        String prefixRaw = perms.rankPrefixForChat(target);
        Optional<GuildRecord> guild = Optional.empty();
        try {
            guild = guilds.guildOf(playerUuid).join();
        } catch (Exception ignored) {
            guild = Optional.empty();
        }

        String teamPrefix = formatPrefix(prefixRaw);
        String teamSuffix = formatGuildSuffix(guild);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = scoreboards.scoreboard(viewer);
            Team team = board.getTeam(teamId(playerUuid));
            if (team == null) {
                team = board.registerNewTeam(teamId(playerUuid));
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            }
            if (!team.hasEntry(nameEntry)) {
                team.addEntry(nameEntry);
            }
            team.setPrefix(teamPrefix);
            team.setSuffix(teamSuffix);
        }
    }

    private static String formatPrefix(String raw) {
        String p = raw == null ? "" : raw.trim();
        if (p.isEmpty()) {
            return "";
        }
        return colorize(p) + ChatColor.RESET + " ";
    }

    private String formatGuildSuffix(Optional<GuildRecord> guild) {
        if (guild == null || guild.isEmpty()) {
            return "";
        }
        GuildRecord g = guild.get();
        String colorLegacy = guilds.legacyColorForGuildTag(g.tagColor());
        String color = (colorLegacy == null || colorLegacy.isBlank()) ? "&f" : colorLegacy;
        String tag = colorize("&8[" + color + g.name() + "&8]");
        return ChatColor.RESET + " " + tag;
    }

    private static String teamId(UUID uuid) {
        String hex = uuid.toString().replace("-", "");
        return ("fx" + hex.substring(0, 14)).toLowerCase(Locale.ROOT);
    }

    private static String colorize(String legacy) {
        return ChatColor.translateAlternateColorCodes('&', legacy);
    }
}

