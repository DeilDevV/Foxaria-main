package com.foxaria.moderation;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.ModerationService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.command.QuietCommandStub;
import com.foxaria.core.staff.StaffPanelSettings;
import com.foxaria.moderation.command.ModerationCommand;
import com.foxaria.moderation.command.ModerationPanelCommand;
import com.foxaria.moderation.command.PunishCommand;
import com.foxaria.moderation.command.StaffUtilityCommand;
import com.foxaria.moderation.command.UnpunishCommand;
import com.foxaria.moderation.listener.ModerationListener;
import com.foxaria.moderation.PunishmentCatalog;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaModerationModule implements FoxariaModule {

    private Listener listener;

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(new MigrationScript(6, "moderation_base", "db/migration/V6__moderation_base.sql"));
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/moderation.yml");
        context.database().applyMigrations(migrations());
        JavaPlugin plugin = context.plugin();
        ProxyNetworkAnnounce.registerOutgoing(plugin);
        FileConfiguration config = context.configs().module("modules/moderation.yml");
        PunishmentCatalog punishmentCatalog = new PunishmentCatalog(config);
        JdbcModerationService service = new JdbcModerationService(plugin, new ModerationRepository(context.database()), context.audits(), context.messages(), config);
        context.services().register(ModerationService.class, service);

        listener = new ModerationListener(plugin, service, context.messages(), config, context.services().optional(SecurityService.class));
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        register(plugin, "warn", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "mute", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "unmute", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "tempmute", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "kick", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "ban", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "unban", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "tempban", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "freeze", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "unfreeze", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "check", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "uncheck", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "report", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "history", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "note", new ModerationCommand(plugin, service, context.messages()));
        register(plugin, "punish", new PunishCommand(plugin, service, context.messages(), punishmentCatalog));
        register(plugin, "unpunish", new UnpunishCommand(plugin, service, context.messages()));
        register(plugin, "staffchat", new StaffUtilityCommand(service, context.messages()));
        register(plugin, "socialspy", new StaffUtilityCommand(service, context.messages()));
        register(plugin, "commandspy", new StaffUtilityCommand(service, context.messages()));
        register(plugin, "vanish", new StaffUtilityCommand(service, context.messages()));
        register(plugin, "invsee", new StaffUtilityCommand(service, context.messages()));
        register(plugin, "echest", new StaffUtilityCommand(service, context.messages()));
        if (StaffPanelSettings.registerPanelCommands(config)) {
            register(plugin, "modpanel", new ModerationPanelCommand(
                service,
                context.services().optional(SecurityService.class),
                context.audits(),
                context.services().require(MenuManager.class),
                context.messages(),
                context.configs()
            ));
        } else {
            register(plugin, "modpanel", QuietCommandStub.INSTANCE);
            org.bukkit.command.PluginCommand modpanel = plugin.getCommand("modpanel");
            if (modpanel != null) {
                modpanel.setTabCompleter(QuietCommandStub.INSTANCE);
            }
        }
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
