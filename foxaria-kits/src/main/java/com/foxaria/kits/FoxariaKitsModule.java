package com.foxaria.kits;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.KitService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.kits.command.KitAdminCommand;
import com.foxaria.kits.command.KitCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaKitsModule implements FoxariaModule {

    @Override
    public String id() {
        return "kits";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(3, "kits_base", "db/migration/V3__kits_base.sql"),
            new MigrationScript(27, "kit_definitions", "db/migration/V27__kit_definitions.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/kits.yml");
        context.database().applyMigrations(migrations());

        KitRepository claimRepository = new KitRepository(context.database());
        KitDefinitionRepository definitionRepository = new KitDefinitionRepository(context.database());
        CompositeKitService kitService = new CompositeKitService(
            context.plugin(),
            context.configs(),
            claimRepository,
            definitionRepository,
            context.messages(),
            context.audits()
        );
        context.services().register(KitService.class, kitService);
        kitService.refreshDatabaseKits();

        MenuManager menuManager = context.services().require(MenuManager.class);
        JavaPlugin plugin = context.plugin();
        KitCommand kitCommand = new KitCommand(kitService, context.messages(), menuManager);
        PluginCommand kit = plugin.getCommand("kit");
        PluginCommand kits = plugin.getCommand("kits");
        if (kit != null) {
            kit.setExecutor(kitCommand);
            kit.setTabCompleter(kitCommand);
        }
        if (kits != null) {
            kits.setExecutor(kitCommand);
            kits.setTabCompleter(kitCommand);
        }

        KitAdminCommand adminCommand = new KitAdminCommand(kitService, definitionRepository, context.messages());
        PluginCommand kitadmin = plugin.getCommand("kitadmin");
        if (kitadmin != null) {
            kitadmin.setExecutor(adminCommand);
            kitadmin.setTabCompleter(adminCommand);
        }
    }

    @Override
    public void stop() {
    }
}
