package com.foxaria.ranks;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.RankService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.ranks.command.RankCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.command.PluginCommand;

import java.util.List;

public final class FoxariaRanksModule implements FoxariaModule {

    private StandaloneRankService rankService;
    private Listener listener;

    @Override
    public String id() {
        return "ranks";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(15, "ranks_base", "db/migration/V15__ranks_base.sql"));
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/ranks.yml");
        var cfg = context.configs().module("modules/ranks.yml");
        if (!cfg.getBoolean("enabled", true)) {
            context.plugin().getLogger().info("Module ranks is disabled in modules/ranks.yml");
            return;
        }
        context.database().applyMigrations(migrations());
        rankService = new StandaloneRankService(
            context.plugin(),
            new FoxariaRankRepository(context.database()),
            cfg
        );
        rankService.start();
        context.services().register(RankService.class, rankService);
        listener = new RankListener(rankService);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        PluginCommand rank = context.plugin().getCommand("rank");
        if (rank != null) {
            RankCommand command = new RankCommand(rankService, context.messages(), cfg, context.services().require(MenuManager.class));
            rank.setExecutor(command);
            rank.setTabCompleter(command);
        }
    }

    @Override
    public void stop() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (rankService != null) {
            rankService.stop();
        }
    }
}
