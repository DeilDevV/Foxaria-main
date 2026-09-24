package com.foxaria.guilds;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.GuildProfileService;
import com.foxaria.api.service.GuildWarService;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.PermissionService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.service.FoxariaPermissionService;
import com.foxaria.core.service.PlayerScoreboardService;
import com.foxaria.guilds.bridge.GuildProxyBridgeListener;
import com.foxaria.guilds.bridge.GuildProxyResyncTask;
import com.foxaria.guilds.bridge.GuildNametagService;
import com.foxaria.guilds.bridge.ProxyGuildSync;
import com.foxaria.guilds.chat.GuildChatFormatListener;
import com.foxaria.guilds.command.GuildCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public final class FoxariaGuildsModule implements FoxariaModule {

    private final List<Listener> listeners = new ArrayList<>();
    private GuildProxyResyncTask guildProxyResyncTask;
    private GuildNametagService nametagService;

    @Override
    public String id() {
        return "guilds";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        /* Версии 24–25 в fx_schema_history уже заняты другими модулями (item-templates, regions). */
        return List.of(
            new MigrationScript(19, "guilds_base", "db/migration/V19__guilds_base.sql"),
            new MigrationScript(20, "guilds_war_prep", "db/migration/V20__guilds_war_prep.sql"),
            new MigrationScript(21, "guilds_features", "db/migration/V21__guilds_features.sql"),
            new MigrationScript(22, "guild_levels_rewards", "db/migration/V22__guild_levels_rewards.sql"),
            new MigrationScript(23, "guild_tag_and_progress", "db/migration/V23__guild_tag_and_progress.sql"),
            new MigrationScript(31, "guild_level_objectives", "db/migration/V31__guild_level_objectives.sql"),
            new MigrationScript(32, "guild_quest_chain_reset", "db/migration/V32__guild_quest_chain_reset.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/guilds.yml");
        context.configs().saveDefault("modules/guilds-gui.yml");
        context.configs().saveDefault("modules/guilds-shop.yml");
        context.configs().saveDefault("modules/guilds-levels.yml");
        context.database().applyMigrations(migrations());

        GuildRepository repository = new GuildRepository(context.database());
        ItemTemplateService itemTemplates = context.services().optional(ItemTemplateService.class);
        GuildService service = new GuildService(
            context.plugin(),
            repository,
            context.services().require(EconomyService.class),
            context.services().require(MenuManager.class),
            context.configs(),
            context.messages(),
            context.audits(),
            itemTemplates
        );
        GuildWarEngine warEngine = new GuildWarEngine(
            context.plugin(),
            repository,
            context.messages(),
            service,
            context.configs().module("modules/guilds.yml").getLong("war.duration-seconds", 1800L) * 1000L
        );
        service.setWarEngine(warEngine);
        context.services().register(GuildService.class, service);
        context.services().register(GuildWarService.class, new ActiveGuildWarService(warEngine));
        context.services().register(GuildProfileService.class, new ActiveGuildProfileService(repository, service));

        ProxyGuildSync.registerOutgoing(context.plugin());
        listeners.add(new GuildProxyBridgeListener(context.plugin(), service));
        guildProxyResyncTask = new GuildProxyResyncTask(context.plugin(), service);
        guildProxyResyncTask.start();

        PermissionService permissionService = context.services().require(PermissionService.class);
        if (permissionService instanceof FoxariaPermissionService foxPerms) {
            listeners.add(new GuildChatFormatListener(foxPerms, service));
            PlayerScoreboardService boards = context.services().optional(PlayerScoreboardService.class);
            if (boards != null) {
                nametagService = new GuildNametagService(context.plugin(), boards, foxPerms, service);
                nametagService.start();
            }
        }

        GuildCommand command = new GuildCommand(service, warEngine, context.messages());
        PluginCommand guildCommand = context.plugin().getCommand("guild");
        if (guildCommand != null) {
            guildCommand.setExecutor(command);
            guildCommand.setTabCompleter(command);
        }
        PluginCommand shortCommand = context.plugin().getCommand("g");
        if (shortCommand != null) {
            shortCommand.setExecutor(command);
            shortCommand.setTabCompleter(command);
        }

        listeners.add(new GuildMobRewardListener(service));
        listeners.add(new GuildLevelProgressListener(service));
        listeners.add(new GuildChestListener(service));
        listeners.add(new GuildPvpListener(service));
        listeners.add(new GuildWarListener(warEngine));
        for (Listener listener : listeners) {
            context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());
        }
    }

    @Override
    public void stop() {
        if (guildProxyResyncTask != null) {
            guildProxyResyncTask.stop();
            guildProxyResyncTask = null;
        }
        if (nametagService != null) {
            nametagService.stop();
            nametagService = null;
        }
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
    }
}
