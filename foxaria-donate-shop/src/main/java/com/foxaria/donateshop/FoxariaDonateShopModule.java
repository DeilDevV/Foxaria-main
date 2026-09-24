package com.foxaria.donateshop;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.donateshop.command.DonateShopCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaDonateShopModule implements FoxariaModule {

    @Override
    public String id() {
        return "donate-shop";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(28, "donate_shop", "db/migration/V28__donate_shop.sql"));
    }

    @Override
    public void start(ModuleContext context) {
        context.database().applyMigrations(migrations());
        EconomyService economy = context.services().require(EconomyService.class);
        MenuManager menuManager = context.services().require(MenuManager.class);
        DonateShopRepository repository = new DonateShopRepository(context.database());
        DonateShopService service = new DonateShopService(
            context.plugin(),
            economy,
            context.messages(),
            context.audits(),
            menuManager,
            repository
        );
        DonateShopCommand command = new DonateShopCommand(service, context.messages());
        PluginCommand cmd = context.plugin().getCommand("donateshop");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
    }

    @Override
    public void stop() {
    }
}
