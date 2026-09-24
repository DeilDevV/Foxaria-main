package com.foxaria.core.service;

import com.foxaria.api.model.BalanceSnapshot;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.GuildProfileService;
import com.foxaria.api.service.KnowledgeService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.core.text.FoxariaColors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SidebarService implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final ServiceRegistry services;
    private final PlayerScoreboardService scoreboards;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");
    private final java.util.concurrent.ConcurrentHashMap<UUID, List<String>> lastEntries = new java.util.concurrent.ConcurrentHashMap<>();
    private BukkitTask updateTask;

    public SidebarService(JavaPlugin plugin, ConfigService configs, ServiceRegistry services, PlayerScoreboardService scoreboards) {
        this.plugin = plugin;
        this.configs = configs;
        this.services = services;
        this.scoreboards = scoreboards;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long interval = Math.max(40L, config().getLong("sidebar.update-interval-ticks", 100L));
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshOnline, 20L, interval);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        // Детач делается scoreboards.stop(); здесь просто перестаём обновлять.
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Детач делает PlayerScoreboardService
    }

    public void refreshOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        if (playerFlowService != null && !playerFlowService.isMainStage(player)) {
            return;
        }

        if (!config().getBoolean("sidebar.enabled", true)) {
            return;
        }

        EconomyService economyService = services.optional(EconomyService.class);
        RankService rankService = services.optional(RankService.class);
        GuildProfileService guildProfileService = services.optional(GuildProfileService.class);
        KnowledgeService knowledgeService = services.optional(KnowledgeService.class);
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        int onlineCount = Bukkit.getOnlinePlayers().size();

        CompletableFuture<BalanceSnapshot> balanceFuture = economyService == null
            ? CompletableFuture.completedFuture(new BalanceSnapshot(playerUuid, BigDecimal.ZERO, 0L, 0L))
            : economyService.balance(playerUuid).exceptionally(ignored ->
                new BalanceSnapshot(playerUuid, BigDecimal.ZERO, 0L, 0L)
            );

        CompletableFuture<String> rankFuture = rankService == null
            ? CompletableFuture.completedFuture("default")
            : rankService.primaryGroup(playerUuid).exceptionally(ignored -> "default");

        CompletableFuture<String> guildFuture = guildProfileService == null
            ? CompletableFuture.completedFuture("&fНет")
            : guildProfileService.guildNameOf(playerUuid).thenApply(opt -> opt.orElse("&fНет")).exceptionally(ignored -> "&fНет");

        CompletableFuture<Integer> knowledgeFuture = knowledgeService == null
            ? CompletableFuture.completedFuture(1)
            : knowledgeService.knowledgeLevel(playerUuid).exceptionally(ignored -> 1);

        CompletableFuture.allOf(balanceFuture, rankFuture, guildFuture, knowledgeFuture).thenAccept(ignored -> {
            BalanceSnapshot balance = balanceFuture.join();
            String rank = rankFuture.join();
            String guild = guildFuture.join();
            int kl = knowledgeFuture.join();
            FoxariaPermissionService foxPerms = services.optional(FoxariaPermissionService.class);
            String rankLabel = foxPerms != null ? foxPerms.rankLabelForSidebar(playerUuid, rank) : rankLabelFromConfig(rank);
            SidebarState state = new SidebarState(
                playerUuid,
                playerName,
                rankLabel,
                String.valueOf(Math.max(1, kl)),
                balanceFormat.format(balance.balance()),
                balance.tokens(),
                onlineCount,
                guild
            );
            plugin.getServer().getScheduler().runTask(plugin, () -> apply(player, state));
        });
    }

    private void apply(Player player, SidebarState state) {
        if (!player.isOnline() || !player.getUniqueId().equals(state.playerUuid())) {
            return;
        }
        Scoreboard scoreboard = scoreboards.scoreboard(player);
        String title = colorize(config().getString("sidebar.title", "&6&lFOXARIA"));
        Objective objective = scoreboard.getObjective("foxaria");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("foxaria", Criteria.DUMMY, title);
        } else {
            objective.setDisplayName(title);
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        // Очистить только старые строки сайдбара, не трогая player-entries (нужны для team-неймтегов).
        List<String> prev = lastEntries.remove(player.getUniqueId());
        if (prev != null) {
            for (String old : prev) {
                scoreboard.resetScores(old);
            }
        }

        List<String> configuredLines = config().getStringList("sidebar.lines");
        List<String> lines = configuredLines.isEmpty() ? new ArrayList<>(defaultLines()) : new ArrayList<>(configuredLines);
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        List<String> rendered = new ArrayList<>();
        for (String line : lines) {
            rendered.add(replacePlaceholders(line, state, kills, deaths));
        }

        Set<String> used = new HashSet<>();
        int score = Math.min(15, rendered.size());
        List<String> nowEntries = new ArrayList<>();
        for (int index = 0; index < score; index++) {
            String line = unique(colorize(rendered.get(index)), used, index);
            objective.getScore(line).setScore(score - index);
            nowEntries.add(line);
        }
        lastEntries.put(player.getUniqueId(), nowEntries);
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    private List<String> defaultLines() {
        return List.of(
            "&7Полуанархия",
            "&8▸ &fПрофиль",
            "&7 Ник: &6<player>",
            "&7 Ранг: <rank>",
            "&7 Знания: &d<knowledge>",
            "&7 Гильдия: <guild>",
            "&8▸ &fСтатистика",
            "&7 Смерти: &c<deaths>",
            "&7 Киллы: &4<kills>",
            "&7 Онлайн: &3<online>",
            "&8▸ &fВалюта",
            "&7 Баланс: &a<balance>",
            "&7 Токены: &b<tokens>",
            "",
            "&6/menu &7- меню сервера"
        );
    }

    private String replacePlaceholders(String line, SidebarState state, int kills, int deaths) {
        String k = String.valueOf(Math.max(0, kills));
        String d = String.valueOf(Math.max(0, deaths));
        return line
            .replace("<player>", state.playerName())
            .replace("<rank>", state.rank())
            .replace("<knowledge>", state.knowledge())
            .replace("<balance>", state.balance())
            .replace("<tokens>", String.valueOf(state.tokens()))
            .replace("<guild>", state.guild())
            .replace("<online>", String.valueOf(state.online()))
            .replace("<world>", "")
            .replace("<kills>", k)
            .replace("<kill>", k)
            .replace("<deaths>", d)
            .replace("<death>", d);
    }

    private String unique(String line, Set<String> used, int index) {
        String value = line;
        String suffix = ChatColor.values()[index % ChatColor.values().length].toString();
        while (!used.add(value)) {
            value = value + suffix;
        }
        return value;
    }

    private String colorize(String value) {
        // Через FoxariaColors: префиксы донат-рангов приходят в hex (&#RRGGBB),
        // обычный translateAlternateColorCodes их не понимал — цвет доната не применялся.
        return FoxariaColors.colorize(value);
    }

    private String rankLabelFromConfig(String groupId) {
        FileConfiguration ranksCfg;
        try {
            ranksCfg = configs.module("modules/ranks.yml");
        } catch (Exception ignored) {
            return "&7" + humanizeRank(groupId);
        }
        ConfigurationSection sec = groupSection(ranksCfg, groupId);
        if (sec == null) {
            return "&7" + humanizeRank(groupId);
        }
        String prefix = stripPrefixBar(sec.getString("prefix", ""));
        if (prefix != null && !prefix.isBlank()) {
            return prefix;
        }
        return sec.getString("display-name", humanizeRank(groupId));
    }

    private ConfigurationSection groupSection(FileConfiguration cfg, String groupId) {
        if (cfg == null || groupId == null || groupId.isBlank()) {
            return null;
        }
        ConfigurationSection byExact = cfg.getConfigurationSection("groups." + groupId);
        if (byExact != null) {
            return byExact;
        }
        return cfg.getConfigurationSection("groups." + groupId.toLowerCase(Locale.ROOT));
    }

    private String stripPrefixBar(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        int idx = prefix.indexOf("&8|");
        if (idx > 0) {
            return prefix.substring(0, idx).trim();
        }
        return prefix.trim();
    }

    private String humanizeRank(String group) {
        return switch (group == null ? "default" : group.toLowerCase(Locale.ROOT)) {
            case "supporter" -> "Поддержка";
            case "vip" -> "VIP";
            case "elite" -> "Элита";
            case "helper" -> "Хелпер";
            case "moderator" -> "Модератор";
            case "admin" -> "Админ";
            default -> "Игрок";
        };
    }

    private FileConfiguration config() {
        return configs.module("modules/player-ui.yml");
    }

    private record SidebarState(
        UUID playerUuid,
        String playerName,
        String rank,
        String knowledge,
        String balance,
        long tokens,
        int online,
        String guild
    ) {
    }
}
