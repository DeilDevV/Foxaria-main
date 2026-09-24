package com.foxaria.regions;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.RegionInteractionGuard;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.regions.command.RaidCommand;
import com.foxaria.regions.command.RegionAdminCommand;
import com.foxaria.regions.command.RegionCommand;
import com.foxaria.regions.listener.RegionCombatListener;
import com.foxaria.regions.listener.RegionExplosionListener;
import com.foxaria.regions.listener.RegionHudListener;
import com.foxaria.regions.listener.RegionHudTask;
import com.foxaria.regions.listener.RegionBoundaryParticleTask;
import com.foxaria.regions.listener.RegionForeignProtectionListener;
import com.foxaria.regions.listener.RegionIntrusionListener;
import com.foxaria.regions.listener.RegionRegenTask;
import com.foxaria.regions.listener.RegionCoreRestoreListener;
import com.foxaria.regions.listener.RegionWorldListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaRegionsModule implements FoxariaModule {

    @Override
    public String id() {
        return "regions";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(25, "regions", "db/migration/V25__regions.sql"),
            new MigrationScript(26, "regions", "db/migration/V26__regions_display_prefs.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        JavaPlugin plugin = context.plugin();
        context.configs().saveDefault("modules/regions.yml");
        context.database().applyMigrations(migrations());

        FileConfiguration yml = context.configs().module("modules/regions.yml");
        RegionConfig cfg = RegionConfig.from(yml);
        RegionRepository repo = new RegionRepository(context.database());
        RegionManager manager = new RegionManager();
        manager.replaceAll(repo.loadAllRegions().join());
        // Прогреваем кэш состава приватов на старте: иначе первые проверки
        // членства (движение, бой, блоки) шли бы мимо кэша.
        manager.snapshot().forEach(region -> repo.memberUuids(region.id()));
        manager.setVerticalBounds(cfg.regionBlocksDown, cfg.regionBlocksUp);

        RegionDamageService damage = new RegionDamageService(plugin, cfg, repo, manager, context.messages());
        RegionFacade facade = new RegionFacade(
            plugin,
            cfg,
            repo,
            manager,
            context.services().require(EconomyService.class),
            damage,
            context.messages(),
            context.services().require(MenuManager.class)
        );
        context.services().register(RegionInteractionGuard.class, facade);

        register(plugin, "region", new RegionCommand(facade));
        register(plugin, "raid", new RaidCommand(facade));
        register(plugin, "regionadmin", new RegionAdminCommand(facade));

        plugin.getServer().getPluginManager().registerEvents(new RegionWorldListener(facade), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RegionCoreRestoreListener(facade), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RegionForeignProtectionListener(facade), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RegionCombatListener(facade, damage), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RegionExplosionListener(plugin, damage), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RegionIntrusionListener(facade), plugin);

        RegionHudListener hud = new RegionHudListener(facade);
        plugin.getServer().getPluginManager().registerEvents(hud, plugin);
        new RegionHudTask(plugin, hud).runTaskTimer(plugin, 5L, 5L);
        new RegionRegenTask(plugin, cfg, repo, manager).runTaskTimer(plugin, cfg.regenIntervalTicks, cfg.regenIntervalTicks);
        new RegionBoundaryParticleTask(facade).runTaskTimer(plugin, 20L, cfg.boundaryParticlePeriodTicks);
    }

    @Override
    public void stop() {
    }

    private static void register(JavaPlugin plugin, String name, org.bukkit.command.CommandExecutor ex) {
        var cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("Command missing in plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(ex);
        if (ex instanceof org.bukkit.command.TabCompleter tab) {
            cmd.setTabCompleter(tab);
        }
    }
}
