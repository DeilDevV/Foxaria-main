package com.foxaria.crafting;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.KnowledgeService;
import com.foxaria.api.service.SmeltBonusHook;
import com.foxaria.crafting.smelt.CraftFurnaceSmeltListener;
import com.foxaria.crafting.smelt.CraftSmeltBonusHook;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.crafting.admin.CraftAdminListener;
import com.foxaria.crafting.command.AdminFoxCraftCommand;
import com.foxaria.crafting.command.CraftCommand;
import com.foxaria.crafting.listener.CraftItemListener;
import com.foxaria.crafting.listener.CraftMenuAnimCleanupListener;
import com.foxaria.crafting.listener.CraftPrepareListener;
import com.foxaria.regions.RegionConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class FoxariaCraftingModule implements FoxariaModule {

    private final List<Listener> listeners = new ArrayList<>();
    private CraftingService crafting;

    @Override
    public String id() {
        return "crafting";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "item-templates", "shop", "regions");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(33, "custom_crafts", "db/migration/V33__custom_crafts.sql"),
            new MigrationScript(37, "craft_sulfur_info", "db/migration/V37__craft_sulfur_info.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        JavaPlugin plugin = context.plugin();
        context.database().applyMigrations(migrations());

        CraftRepository repo = new CraftRepository(context.database());
        ItemTemplateService templates = context.services().require(ItemTemplateService.class);
        KnowledgeService knowledge = context.services().require(KnowledgeService.class);
        crafting = new CraftingService(plugin, repo, templates, knowledge);
        crafting.bootstrap();

        FileConfiguration regionsYml = context.configs().module("modules/regions.yml");
        RegionConfig regionConfig = RegionConfig.from(regionsYml);
        MenuManager menus = context.services().require(MenuManager.class);

        CraftPrepareListener prep = new CraftPrepareListener(crafting);
        CraftItemListener craft = new CraftItemListener(crafting);
        CraftAdminListener adminGui = new CraftAdminListener(plugin, menus);
        SmeltBonusHook smeltHook = new CraftSmeltBonusHook(crafting.smeltBonuses());
        context.services().register(SmeltBonusHook.class, smeltHook);
        CraftFurnaceSmeltListener furnaceSmelt = new CraftFurnaceSmeltListener(plugin, smeltHook);
        CraftMenuAnimCleanupListener animCleanup = new CraftMenuAnimCleanupListener();
        listeners.add(prep);
        listeners.add(craft);
        listeners.add(adminGui);
        listeners.add(furnaceSmelt);
        listeners.add(animCleanup);
        for (Listener listener : listeners) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        CraftCommand craftCmd = new CraftCommand(crafting, regionConfig, knowledge, context.messages(), menus);
        PluginCommand craftPluginCmd = plugin.getCommand("craft");
        if (craftPluginCmd != null) {
            craftPluginCmd.setExecutor(craftCmd);
            craftPluginCmd.setTabCompleter(null);
        } else {
            plugin.getLogger().warning("Command missing in plugin.yml: craft");
        }

        AdminFoxCraftCommand adminCmd = new AdminFoxCraftCommand(crafting, context.messages());
        PluginCommand adminPluginCmd = plugin.getCommand("adminfoxcraft");
        if (adminPluginCmd != null) {
            adminPluginCmd.setExecutor(adminCmd);
        } else {
            plugin.getLogger().warning("Command missing in plugin.yml: adminfoxcraft");
        }
    }

    @Override
    public void stop() {
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
        if (crafting != null) {
            crafting.registry().clear(crafting.plugin());
        }
        // SmeltBonusHook остаётся в реестре до перезапуска плагина — не снимаем вручную.
    }
}
