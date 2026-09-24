package com.foxaria.cases;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.cases.service.CaseService;
import com.foxaria.cases.service.ConfigCaseService;
import com.foxaria.core.gui.MenuManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaCasesModule implements FoxariaModule {

    private CasesApplication application;

    @Override
    public String id() {
        return "cases";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of();
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/cases.yml");
        JavaPlugin plugin = context.plugin();
        application = new CasesApplication(
            plugin,
            context.services().require(MenuManager.class),
            context.messages(),
            context.audits(),
            new CasesApplication.CaseDatabase() {
                @Override
                public com.foxaria.api.service.DatabaseGateway gateway() {
                    return context.database();
                }

                @Override
                public void applyMigrations(List<MigrationScript> migrations) {
                    context.database().applyMigrations(migrations);
                }
            }
        );
        application.start(context.configs().module("modules/cases.yml"));
        context.services().register(CaseService.class, application.caseService());
        context.services().register(ConfigCaseService.class, application.config());
    }

    @Override
    public void stop() {
        if (application != null) {
            application.stop();
        }
    }
}
