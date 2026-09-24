package com.foxaria.security;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.SecurityService;
import com.foxaria.security.command.GrimHookCommand;
import com.foxaria.security.command.SecurityCommand;
import com.foxaria.security.listener.SecurityListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;

import java.util.List;

public final class FoxariaSecurityModule implements FoxariaModule {

    private Listener listener;

    @Override
    public String id() {
        return "security";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(7, "security_base", "db/migration/V7__security_base.sql"));
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/security.yml");
        context.database().applyMigrations(migrations());
        FileConfiguration securityConfig = context.configs().module("modules/security.yml");

        JdbcSecurityService securityService = new JdbcSecurityService(
            context.database(),
            context.audits(),
            securityConfig.getLong("command-window-millis", 5000L),
            securityConfig.getInt("max-command-hits", 12)
        );
        context.services().register(SecurityService.class, securityService);

        listener = new SecurityListener(context.plugin(), securityService, context.messages(), securityConfig);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        PluginCommand command = context.plugin().getCommand("security");
        if (command != null) {
            command.setExecutor(new SecurityCommand(securityService, context.integrations(), context.messages()));
        }
        PluginCommand grimhook = context.plugin().getCommand("grimhook");
        if (grimhook != null) {
            grimhook.setExecutor(new GrimHookCommand(securityService));
        }
    }

    @Override
    public void stop() {
    }
}
