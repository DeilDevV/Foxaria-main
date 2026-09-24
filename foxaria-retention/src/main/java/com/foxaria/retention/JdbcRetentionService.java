package com.foxaria.retention;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.RetentionService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class JdbcRetentionService implements RetentionService {

    private final JavaPlugin plugin;
    private final RetentionRepository repository;
    private final EconomyService economyService;
    private final MessageService messages;
    private final AuditService audits;
    private final FileConfiguration config;
    private final List<Long> milestones;
    private final List<BigDecimal> playtimeRewardCoins;
    private final BigDecimal streakReward;
    private final DiscordWebhookClient discordWebhookClient;

    public JdbcRetentionService(
        JavaPlugin plugin,
        RetentionRepository repository,
        EconomyService economyService,
        MessageService messages,
        AuditService audits,
        FileConfiguration config,
        List<Long> milestones,
        List<BigDecimal> playtimeRewardCoins,
        BigDecimal streakReward
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.economyService = economyService;
        this.messages = messages;
        this.audits = audits;
        this.config = config;
        this.milestones = milestones;
        this.playtimeRewardCoins = playtimeRewardCoins;
        this.streakReward = streakReward;
        this.discordWebhookClient = config.getBoolean("discord.enabled", false) && !config.getString("discord.webhook-url", "").isBlank()
            ? new DiscordWebhookClient(config.getString("discord.webhook-url", ""), config.getString("discord.username", "Foxaria Ops"))
            : null;
    }

    @Override
    public CompletableFuture<Void> onJoin(Player player) {
        LocalDate today = LocalDate.now();
        return repository.streak(player.getUniqueId()).thenCompose(snapshot -> {
            int streak = snapshot.streakDays();
            if (snapshot.lastLoginDate().equals(today)) {
                return CompletableFuture.completedFuture(null);
            }
            if (snapshot.lastLoginDate().plusDays(1).equals(today)) {
                streak += 1;
            } else {
                streak = 1;
            }
            final int finalStreak = streak;
            return repository.updateStreak(player.getUniqueId(), today, streak).thenCompose(ignored ->
                economyService.deposit(player.getUniqueId(), streakReward, "daily_streak", null).thenRun(() -> {
                    messages.send(player, "retention.streak", "&aLogin streak: <streak> days. Reward issued.", new MessageService.Placeholder("streak", String.valueOf(finalStreak)));
                    audits.append(new AuditEvent(
                        "RETENTION_STREAK_REWARD",
                        player.getUniqueId(),
                        null,
                        player.getName(),
                        null,
                        "Daily streak reward issued",
                        Map.of("streak", String.valueOf(finalStreak)),
                        System.currentTimeMillis()
                    ));
                    sendDiscordHook("Login streak: " + player.getName() + " reached " + finalStreak + " days.");
                })
            );
        });
    }

    @Override
    public CompletableFuture<Integer> streak(Player player) {
        return repository.streak(player.getUniqueId()).thenApply(RetentionRepository.StreakSnapshot::streakDays);
    }

    public CompletableFuture<Void> claimPlaytimeRewards(Player player) {
        long playtimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        return repository.claimedPlaytimeRewards(player.getUniqueId()).thenCompose(claimed -> {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            AtomicInteger granted = new AtomicInteger();
            BigDecimal[] total = {BigDecimal.ZERO};
            for (int i = 0; i < milestones.size(); i++) {
                long milestone = milestones.get(i);
                if (playtimeSeconds < milestone || claimed.containsKey(milestone)) {
                    continue;
                }
                BigDecimal coins = rewardForMilestoneIndex(i, milestone);
                chain = chain.thenCompose(ignored -> repository.markPlaytimeClaimed(player.getUniqueId(), milestone)
                    .thenCompose(nothing -> economyService.deposit(player.getUniqueId(), coins, "playtime_reward", null))
                    .thenRun(() -> {
                        granted.incrementAndGet();
                        total[0] = total[0].add(coins);
                    })
                );
            }
            return chain.thenRun(() -> {
                int n = granted.get();
                if (n == 0) {
                    messages.send(player, "retention.playtime-none", "&7Новых наград за наигранное время нет. Играй дальше и снова открой &f/rewards&7.");
                } else {
                    messages.send(player, "retention.playtime-granted", "&aВыдано наград: &f<n>&a на сумму &f<c>&a монет.",
                        new MessageService.Placeholder("n", String.valueOf(n)),
                        new MessageService.Placeholder("c", total[0].toPlainString()));
                }
            });
        });
    }

    private BigDecimal rewardForMilestoneIndex(int index, long milestoneSeconds) {
        if (index >= 0 && index < playtimeRewardCoins.size()) {
            BigDecimal v = playtimeRewardCoins.get(index);
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                return v.setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.valueOf(milestoneSeconds / 60.0D).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public CompletableFuture<Void> claimVoteReward(Player player, String siteKey) {
        String normalizedSite = siteKey == null || siteKey.isBlank() ? "default" : siteKey.toLowerCase(java.util.Locale.ROOT);
        if (!config.getStringList("vote.allowed-sites").isEmpty() && !config.getStringList("vote.allowed-sites").contains(normalizedSite)) {
            messages.send(player, "retention.vote-invalid", "&cThat vote source is not enabled.");
            return CompletableFuture.completedFuture(null);
        }
        BigDecimal reward = BigDecimal.valueOf(config.getDouble("vote.coins-per-claim", 50.0D));
        return repository.claimVote(player.getUniqueId(), normalizedSite, LocalDate.now()).thenCompose(claimed -> {
            if (!claimed) {
                messages.send(player, "retention.vote-already", "&cYou already claimed that vote reward today.");
                return CompletableFuture.completedFuture(null);
            }
            return economyService.deposit(player.getUniqueId(), reward, "vote_reward:" + normalizedSite, null).thenRun(() -> {
                messages.send(player, "retention.vote-claimed", "&aVote reward claimed from <site>.",
                    new MessageService.Placeholder("site", normalizedSite));
                audits.append(new AuditEvent(
                    "VOTE_REWARD_CLAIMED",
                    player.getUniqueId(),
                    player.getUniqueId(),
                    player.getName(),
                    player.getName(),
                    "Vote reward claimed",
                    Map.of("site", normalizedSite, "amount", reward.toPlainString()),
                    System.currentTimeMillis()
                ));
                sendDiscordHook("Vote reward claimed: " + player.getName() + " via " + normalizedSite + ".");
            });
        });
    }

    public CompletableFuture<Void> applyReferral(Player referred, OfflinePlayer referrer) {
        if (!config.getBoolean("referrals.enabled", true)) {
            messages.send(referred, "retention.referral-disabled", "&cReferrals are disabled.");
            return CompletableFuture.completedFuture(null);
        }
        if (referrer == null || referrer.getUniqueId().equals(referred.getUniqueId())) {
            messages.send(referred, "retention.referral-invalid", "&cThat referral is not valid.");
            return CompletableFuture.completedFuture(null);
        }
        long maxPlaytime = config.getLong("referrals.max-playtime-seconds", 7200L);
        long currentPlaytime = referred.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        if (currentPlaytime > maxPlaytime) {
            messages.send(referred, "retention.referral-too-late", "&cYour account is too old to use a referral.");
            return CompletableFuture.completedFuture(null);
        }
        BigDecimal reward = BigDecimal.valueOf(config.getDouble("referrals.reward-coins", 100.0D));
        return repository.createReferral(referrer.getUniqueId(), referred.getUniqueId()).thenCompose(created -> {
            if (!created) {
                messages.send(referred, "retention.referral-already", "&cYou already set a referral.");
                return CompletableFuture.completedFuture(null);
            }
            return economyService.deposit(referred.getUniqueId(), reward, "referral_referred", null)
                .thenCompose(ignored -> economyService.deposit(referrer.getUniqueId(), reward, "referral_referrer", null))
                .thenRun(() -> {
                    messages.send(referred, "retention.referral-success", "&aReferral applied. Rewards have been issued.");
                    Player onlineReferrer = referrer.getPlayer();
                    if (onlineReferrer != null) {
                        messages.send(onlineReferrer, "retention.referral-received", "&aReferral reward received for inviting <player>.",
                            new MessageService.Placeholder("player", referred.getName()));
                    }
                    audits.append(new AuditEvent(
                        "REFERRAL_CREATED",
                        referred.getUniqueId(),
                        referrer.getUniqueId(),
                        referred.getName(),
                        referrer.getName(),
                        "Referral created",
                        Map.of("reward", reward.toPlainString()),
                        System.currentTimeMillis()
                    ));
                    sendDiscordHook("Referral: " + referred.getName() + " joined via " + referrer.getName() + ".");
                });
        });
    }

    public String seasonSummary() {
        return config.getString("season.name", "Foxaria Season")
            + " | Ends: " + config.getString("season.ends-at", "TBD")
            + " | " + config.getString("season.description", "Long-term semi-anarchy season.");
    }

    public void startAnnouncements(List<String> announcements, long intervalTicks) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (announcements.isEmpty()) {
                return;
            }
            int index = (int) ((System.currentTimeMillis() / 1000L) % announcements.size());
            plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(announcements.get(index)));
        }, intervalTicks, intervalTicks);
    }

    private void sendDiscordHook(String content) {
        if (discordWebhookClient == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                discordWebhookClient.send(content);
            } catch (Exception exception) {
                plugin.getLogger().warning("Discord webhook failed: " + exception.getMessage());
            }
        });
    }
}
