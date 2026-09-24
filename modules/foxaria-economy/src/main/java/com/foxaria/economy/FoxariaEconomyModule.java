package com.foxaria.economy;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.IntegrationService;
import com.foxaria.economy.command.BalanceCommand;
import com.foxaria.economy.command.EconomyAdminCommand;
import com.foxaria.economy.command.PayCommand;
import com.foxaria.economy.command.TokenCommand;
import com.foxaria.economy.vault.VaultEconomyBridge;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaEconomyModule implements FoxariaModule {

    private Listener joinListener;

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(2, "economy_base", "db/migration/V2__economy_base.sql"));
    }

    @Override
    public void start(ModuleContext context) {
        JavaPlugin plugin = context.plugin();
        context.database().applyMigrations(migrations());

        EconomyRepository repository = new EconomyRepository(context.database());
        EconomyService economyService = new JdbcEconomyService(repository, context.audits());
        context.services().register(EconomyService.class, economyService);

        register(plugin, "bal", new BalanceCommand(economyService, context.messages()), null);
        register(plugin, "pay", new PayCommand(economyService, context.messages(), plugin), null);
        register(plugin, "baltop", new BalanceCommand(economyService, context.messages(), true), null);
        register(plugin, "eco", new EconomyAdminCommand(economyService, context.messages(), plugin), null);
        register(plugin, "token", new TokenCommand(economyService, context.messages()), null);

        joinListener = new PlayerAccountListener(economyService);
        plugin.getServer().getPluginManager().registerEvents(joinListener, plugin);

        IntegrationService integrations = context.integrations();
        if (integrations.hasVault()) {
            plugin.getServer().getServicesManager().register(
                Economy.class,
                new VaultEconomyBridge(economyService, plugin),
                plugin,
                ServicePriority.High
            );
        }
    }

    @Override
    public void stop() {
        if (joinListener != null) {
            HandlerList.unregisterAll(joinListener);
        }
    }

    private void register(JavaPlugin plugin, String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Command '" + name + "' is missing in plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }
}
