package com.foxaria.retention;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.RetentionService;
import com.foxaria.retention.command.RetentionCommand;
import com.foxaria.retention.listener.RetentionListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class FoxariaRetentionModule implements FoxariaModule {

    private Listener listener;

    @Override
    public String id() {
        return "retention";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(9, "retention_base", "db/migration/V9__retention_base.sql"),
            new MigrationScript(14, "retention_community", "db/migration/V14__retention_community.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/retention.yml");
        context.database().applyMigrations(migrations());
        var retentionConfig = context.configs().module("modules/retention.yml");
        List<Long> milestones = retentionConfig.getLongList("playtime-milestones-seconds");
        List<Double> coinDoubles = retentionConfig.getDoubleList("playtime-reward-coins");
        List<BigDecimal> playtimeCoins = new ArrayList<>();
        for (Double d : coinDoubles) {
            playtimeCoins.add(BigDecimal.valueOf(d == null ? 0.0D : d));
        }
        if (!playtimeCoins.isEmpty() && playtimeCoins.size() != milestones.size()) {
            context.plugin().getLogger().warning("[retention] playtime-reward-coins must have same length as playtime-milestones-seconds; using fallback formula for missing entries.");
        }
        JdbcRetentionService retentionService = new JdbcRetentionService(
            context.plugin(),
            new RetentionRepository(context.database()),
            context.services().require(EconomyService.class),
            context.messages(),
            context.audits(),
            retentionConfig,
            milestones,
            playtimeCoins,
            BigDecimal.valueOf(retentionConfig.getDouble("daily-streak-reward", 25.0D))
        );
        retentionService.startAnnouncements(
            retentionConfig.getStringList("announcements"),
            retentionConfig.getLong("announcement-interval-ticks", 20L * 300L)
        );
        context.services().register(RetentionService.class, retentionService);

        listener = new RetentionListener(retentionService);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        PluginCommand rewards = context.plugin().getCommand("rewards");
        PluginCommand streak = context.plugin().getCommand("streak");
        PluginCommand voteclaim = context.plugin().getCommand("voteclaim");
        PluginCommand refer = context.plugin().getCommand("refer");
        PluginCommand season = context.plugin().getCommand("season");
        RetentionCommand retentionCommand = new RetentionCommand(retentionService, context.messages());
        if (rewards != null) {
            rewards.setExecutor(retentionCommand);
        }
        if (streak != null) {
            streak.setExecutor(retentionCommand);
        }
        if (voteclaim != null) {
            voteclaim.setExecutor(retentionCommand);
        }
        if (refer != null) {
            refer.setExecutor(retentionCommand);
        }
        if (season != null) {
            season.setExecutor(retentionCommand);
        }
    }

    @Override
    public void stop() {
    }
}
