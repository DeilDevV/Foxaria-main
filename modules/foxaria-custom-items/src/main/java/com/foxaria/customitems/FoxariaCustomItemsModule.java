package com.foxaria.customitems;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.CustomItemService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.customitems.command.CrateCommand;
import com.foxaria.customitems.listener.CustomItemsListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;

import java.util.List;

public final class FoxariaCustomItemsModule implements FoxariaModule {

    private Listener listener;

    @Override
    public String id() {
        return "custom-items";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(8, "custom_items_base", "db/migration/V8__custom_items_base.sql"),
            new MigrationScript(12, "custom_items_rewards", "db/migration/V12__custom_items_rewards.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/custom-items.yml");
        context.database().applyMigrations(migrations());

        var customItemsConfig = context.configs().module("modules/custom-items.yml");
        CustomItemsRepository repository = new CustomItemsRepository(context.database());
        PdcCustomItemService service = new PdcCustomItemService(context.plugin());
        RewardItemFactory rewardFactory = new RewardItemFactory(service, repository, customItemsConfig);
        ConfigCrateService crateService = new ConfigCrateService(
            context.plugin(),
            customItemsConfig,
            context.messages(),
            context.audits(),
            context.services().require(EconomyService.class),
            context.services().require(MenuManager.class),
            service,
            rewardFactory,
            repository
        );

        context.services().register(CustomItemService.class, service);
        context.services().register(PdcCustomItemService.class, service);
        context.services().register(RewardItemFactory.class, rewardFactory);
        context.services().register(ConfigCrateService.class, crateService);

        listener = new CustomItemsListener(crateService, service);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        PluginCommand crate = context.plugin().getCommand("crate");
        if (crate != null) {
            crate.setExecutor(new CrateCommand(crateService, context.messages()));
        }
    }

    @Override
    public void stop() {
    }
}
