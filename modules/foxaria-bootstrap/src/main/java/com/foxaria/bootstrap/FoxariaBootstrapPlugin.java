package com.foxaria.bootstrap;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.ModuleContext;
import com.foxaria.core.FoxariaCoreModule;
import com.foxaria.core.service.SimpleServiceRegistry;
import com.foxaria.economy.FoxariaEconomyModule;
import com.foxaria.kits.FoxariaKitsModule;
import com.foxaria.shop.FoxariaShopModule;
import com.foxaria.donateshop.FoxariaDonateShopModule;
import com.foxaria.auction.FoxariaAuctionModule;
import com.foxaria.store.FoxariaStoreModule;
import com.foxaria.moderation.FoxariaModerationModule;
import com.foxaria.admin.FoxariaAdminModule;
import com.foxaria.security.FoxariaSecurityModule;
import com.foxaria.customitems.FoxariaCustomItemsModule;
import com.foxaria.retention.FoxariaRetentionModule;
import com.foxaria.guilds.FoxariaGuildsModule;
import com.foxaria.itemtemplates.FoxariaItemTemplatesModule;
import com.foxaria.crafting.FoxariaCraftingModule;
import com.foxaria.modernfurnace.FoxariaModernFurnaceModule;
import com.foxaria.regions.FoxariaRegionsModule;
import com.foxaria.ranks.FoxariaRanksModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FoxariaBootstrapPlugin extends JavaPlugin {

    private final List<FoxariaModule> startedModules = new ArrayList<>();

    @Override
    public void onEnable() {
        validateEnvironment();

        SimpleServiceRegistry serviceRegistry = new SimpleServiceRegistry();
        ModuleContext context = new FoxariaModuleContext(this, serviceRegistry);

        List<FoxariaModule> modules = List.of(
            new FoxariaCoreModule(),
            new FoxariaEconomyModule(),
            new FoxariaDonateShopModule(),
            new FoxariaRegionsModule(),
            new FoxariaKitsModule(),
            new FoxariaItemTemplatesModule(),
            new FoxariaShopModule(),
            new FoxariaCraftingModule(),
            new FoxariaModernFurnaceModule(),
            new FoxariaAuctionModule(),
            new FoxariaSecurityModule(),
            new FoxariaStoreModule(),
            new FoxariaModerationModule(),
            new FoxariaAdminModule(),
            new FoxariaCustomItemsModule(),
            new FoxariaRetentionModule(),
            new FoxariaGuildsModule(),
            new FoxariaRanksModule()
        );

        try {
            for (FoxariaModule module : modules) {
                getLogger().info("Starting module: " + module.id());
                module.start(context);
                startedModules.add(module);
            }
        } catch (Exception exception) {
            getLogger().severe("Foxaria failed to start: " + exception.getMessage());
            exception.printStackTrace();
            Collections.reverse(startedModules);
            for (FoxariaModule module : startedModules) {
                try {
                    module.stop();
                } catch (Exception stopException) {
                    getLogger().warning("Failed to stop module " + module.id() + ": " + stopException.getMessage());
                }
            }
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        Collections.reverse(startedModules);
        for (FoxariaModule module : startedModules) {
            try {
                module.stop();
            } catch (Exception exception) {
                getLogger().warning("Failed to stop module " + module.id() + ": " + exception.getMessage());
            }
        }
        startedModules.clear();
    }

    private void validateEnvironment() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder.");
        }
        int javaFeatureVersion = Runtime.version().feature();
        if (javaFeatureVersion < 21) {
            throw new IllegalStateException("Foxaria requires Java 21+ for Paper/Purpur 1.21.11. Detected: " + javaFeatureVersion);
        }
    }
}
