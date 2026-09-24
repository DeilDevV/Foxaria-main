package com.foxaria.auction;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.AuctionService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.auction.command.AuctionCommand;
import org.bukkit.command.PluginCommand;

import java.math.BigDecimal;
import java.util.List;

public final class FoxariaAuctionModule implements FoxariaModule {

    @Override
    public String id() {
        return "auction";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(5, "auction_base", "db/migration/V5__auction_base.sql"));
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/auction.yml");
        context.database().applyMigrations(migrations());
        AuctionService auctionService = new JdbcAuctionService(
            context.plugin(),
            new AuctionRepository(context.database()),
            context.services().require(EconomyService.class),
            context.messages(),
            context.audits(),
            context.configs().module("modules/auction.yml").getLong("listing-duration-seconds", 172800L),
            BigDecimal.valueOf(context.configs().module("modules/auction.yml").getDouble("listing-fee-percent", 5.0D))
        );
        context.services().register(AuctionService.class, auctionService);

        MenuManager menuManager = context.services().require(MenuManager.class);
        PluginCommand command = context.plugin().getCommand("ah");
        if (command != null) {
            AuctionCommand auctionCommand = new AuctionCommand(context.plugin(), auctionService, context.messages(), menuManager);
            command.setExecutor(auctionCommand);
            command.setTabCompleter(auctionCommand);
        }
    }

    @Override
    public void stop() {
    }
}
