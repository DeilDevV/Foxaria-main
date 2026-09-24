package com.foxaria.playershops;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.PlayerShopService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.playershops.command.PlayerShopCommand;
import org.bukkit.command.PluginCommand;

import java.util.List;

public final class FoxariaPlayerShopsModule implements FoxariaModule {

    @Override
    public String id() {
        return "player-shops";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(11, "player_shops_base", "db/migration/V11__player_shops_base.sql"));
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/player-shops.yml");
        context.database().applyMigrations(migrations());
        JdbcPlayerShopService service = new JdbcPlayerShopService(
            context.plugin(),
            new PlayerShopRepository(context.database()),
            context.services().require(EconomyService.class),
            context.messages(),
            context.audits(),
            context.configs().module("modules/player-shops.yml").getInt("max-active-offers", 24),
            context.configs().module("modules/player-shops.yml").getDouble("default-tax-percent", 4.0D)
        );
        context.services().register(PlayerShopService.class, service);

        PlayerShopCommand command = new PlayerShopCommand(service, context.messages(), context.services().require(MenuManager.class));
        PluginCommand pshop = context.plugin().getCommand("pshop");
        if (pshop != null) {
            pshop.setExecutor(command);
            pshop.setTabCompleter(command);
        }
    }

    @Override
    public void stop() {
    }
}
