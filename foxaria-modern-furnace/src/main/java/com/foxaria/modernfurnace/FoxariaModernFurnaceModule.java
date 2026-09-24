package com.foxaria.modernfurnace;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.RegionInteractionGuard;
import com.foxaria.api.service.SmeltBonusHook;
import com.foxaria.modernfurnace.command.AdminFurnacesCommand;
import com.foxaria.modernfurnace.listener.ModernFurnaceChunkListener;
import com.foxaria.modernfurnace.listener.ModernFurnaceGuiListener;
import com.foxaria.modernfurnace.listener.ModernFurnacePipeChestListener;
import com.foxaria.modernfurnace.listener.ModernFurnaceWorldListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class FoxariaModernFurnaceModule implements FoxariaModule {

    private final List<Listener> listeners = new ArrayList<>();

    @Override
    public String id() {
        return "modern-furnace";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy", "crafting", "regions");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(36, "modern_furnace", "db/migration/V36__modern_furnace.sql"));
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        JavaPlugin plugin = context.plugin();
        context.database().applyMigrations(migrations());

        context.configs().saveDefault("modules/modern-furnace.yml");
        ModernFurnaceConfig config = new ModernFurnaceConfig(context.configs().module("modules/modern-furnace.yml"));

        ModernFurnaceKeys keys = new ModernFurnaceKeys(plugin);
        ModernFurnaceRepository repository = new ModernFurnaceRepository(context.database());
        SmeltBonusHook smeltBonus = context.services().optional(SmeltBonusHook.class);
        ModernFurnaceService service = new ModernFurnaceService(plugin, repository, keys, config, smeltBonus);
        ModernFurnacePending pipePending = new ModernFurnacePending();
        EconomyService economy = context.services().require(EconomyService.class);
        MessageService messages = context.messages();
        RegionInteractionGuard regionGuard = context.services().optional(RegionInteractionGuard.class);

        register(new ModernFurnaceWorldListener(keys, service, config, messages, regionGuard));
        register(new ModernFurnaceGuiListener(plugin, service, config, economy, pipePending));
        register(new ModernFurnacePipeChestListener(pipePending, service, keys));
        register(new ModernFurnaceChunkListener(service));

        for (Listener listener : listeners) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        service.startTicker();
        ModernFurnaceParticles.start(plugin, service);

        PluginCommand cmd = plugin.getCommand("adminfurnaces");
        if (cmd != null) {
            cmd.setExecutor(new AdminFurnacesCommand(keys));
        } else {
            plugin.getLogger().warning("Command missing in plugin.yml: adminfurnaces");
        }
    }

    private void register(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void stop() {
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
    }
}
