package com.foxaria.auth;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaAuthModule implements FoxariaModule {

    private Listener listener;
    private FoxariaAuthService service;

    @Override
    public String id() {
        return "auth";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(16, "auth_base", "db/migration/V16__auth_base.sql"),
            new MigrationScript(17, "auth_uuid_index", "db/migration/V17__auth_uuid_index.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/auth.yml");
        var cfg = context.configs().module("modules/auth.yml");
        if (!cfg.getBoolean("enabled", true)) {
            context.plugin().getLogger().info("Module auth is disabled in modules/auth.yml");
            return;
        }
        context.database().applyMigrations(migrations());

        JavaPlugin plugin = context.plugin();
        service = new FoxariaAuthService(plugin, new AuthRepository(context.database()), context.configs(), context.messages());
        context.services().register(FoxariaAuthService.class, service);
        listener = new AuthListener(service, context.messages());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        AuthCommand authCommand = new AuthCommand(plugin, service, context.messages());
        register(plugin, "register", authCommand);
        register(plugin, "login", authCommand);
    }

    @Override
    public void stop() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (service != null) {
            service.shutdown();
        }
    }

    private void register(JavaPlugin plugin, String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }
}
