package com.foxaria.admin;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ModerationService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.command.QuietCommandStub;
import com.foxaria.core.staff.StaffPanelSettings;
import com.foxaria.admin.command.AdminPanelCommand;
import com.foxaria.admin.wipe.WipeService;
import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.admin.command.DonateAdminCommand;
import com.foxaria.admin.command.FoxariaReloadCommand;
import com.foxaria.admin.command.MaintenanceCommand;
import com.foxaria.admin.command.RestartCountdownCommand;
import com.foxaria.admin.listener.AdminListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaAdminModule implements FoxariaModule {

    private Listener listener;

    @Override
    public String id() {
        return "admin";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy", "moderation");
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/admin.yml");
        FileConfiguration moderationYaml = context.configs().module("modules/moderation.yml");
        AdminState state = new AdminState();
        context.services().register(AdminState.class, state);

        JavaPlugin plugin = context.plugin();
        MenuManager menuManager = context.services().require(MenuManager.class);
        EconomyService economyService = context.services().require(EconomyService.class);
        ModerationService moderationService = context.services().require(ModerationService.class);
        RankService rankService = context.services().require(RankService.class);
        AuditService auditService = context.audits();
        SecurityService securityService = context.services().optional(SecurityService.class);

        // Сервис вайпа: очистка игровых таблиц сезона (донат и наказания не трогает).
        DatabaseGateway database = context.services().require(DatabaseGateway.class);
        WipeService wipeService = new WipeService(plugin, database, context.configs());
        context.services().register(WipeService.class, wipeService);

        if (StaffPanelSettings.registerPanelCommands(moderationYaml)) {
            register(plugin, "adminpanel", new AdminPanelCommand(menuManager, economyService, moderationService, securityService, rankService, auditService, context.messages(), context.configs(), plugin, wipeService));
        } else {
            register(plugin, "adminpanel", QuietCommandStub.INSTANCE);
            PluginCommand adminpanel = plugin.getCommand("adminpanel");
            if (adminpanel != null) {
                adminpanel.setTabCompleter(QuietCommandStub.INSTANCE);
            }
        }
        register(plugin, "fdonate", new DonateAdminCommand(economyService, rankService, context.messages()));
        PluginCommand donateAdmin = plugin.getCommand("fdonate");
        if (donateAdmin != null) {
            donateAdmin.setTabCompleter(new DonateAdminCommand(economyService, rankService, context.messages()));
        }
        register(plugin, "maintenance", new MaintenanceCommand(state, context.messages()));
        register(plugin, "restartcountdown", new RestartCountdownCommand(plugin, context.messages()));
        register(plugin, "foxreload", new FoxariaReloadCommand(plugin, context.messages()));
        register(plugin, "auditlog", new AdminPanelCommand(menuManager, economyService, moderationService, securityService, rankService, auditService, context.messages(), context.configs(), plugin, wipeService));

        listener = new AdminListener(state);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void stop() {
    }

    private void register(JavaPlugin plugin, String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }
}
