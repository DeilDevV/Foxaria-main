package com.foxaria.core;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.AsyncScheduler;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.api.service.IntegrationService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.PermissionService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.core.command.HomeCommand;
import com.foxaria.core.command.HelpCommand;
import com.foxaria.core.command.MenuCommand;
import com.foxaria.core.command.SpawnCommand;
import com.foxaria.core.command.SpeedCommand;
import com.foxaria.core.command.ServerDisabledCommand;
import com.foxaria.core.command.ServerSelectorCommand;
import com.foxaria.core.command.PermDebugCommand;
import com.foxaria.core.command.TeleportRequestCommand;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.listener.CoreGameplayListener;
import com.foxaria.core.listener.FoxariaProxyChatPrefixListener;
import com.foxaria.core.listener.FirstJoinRtpListener;
import com.foxaria.core.listener.FirstJoinRtpGuardListener;
import com.foxaria.core.listener.FirstJoinSpawnLocationListener;
import com.foxaria.core.listener.RespawnHomeOrRtpListener;
import com.foxaria.core.service.BukkitIntegrationService;
import com.foxaria.core.service.CombatTagService;
import com.foxaria.core.service.CoreRepository;
import com.foxaria.core.service.FoxariaPermissionService;
import com.foxaria.core.service.FirstJoinTrackerService;
import com.foxaria.core.service.JdbcAuditService;
import com.foxaria.core.service.JdbcDatabaseGateway;
import com.foxaria.core.service.LagProtectionService;
import com.foxaria.core.service.PermissionBackedRankService;
import com.foxaria.core.service.PaperAsyncScheduler;
import com.foxaria.core.service.PlayerFlowService;
import com.foxaria.core.service.PlayerScoreboardService;
import com.foxaria.core.service.PlayerUiService;
import com.foxaria.core.service.SidebarService;
import com.foxaria.core.service.SleepersService;
import com.foxaria.core.service.SimpleServiceRegistry;
import com.foxaria.core.service.TeleportService;
import com.foxaria.core.service.YamlConfigService;
import com.foxaria.core.service.YamlMessageService;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class FoxariaCoreModule implements FoxariaModule {

    private JavaPlugin plugin;

    private CoreGameplayListener listener;
    private LagProtectionService lagProtectionService;
    private SidebarService sidebarService;
    private FoxariaProxyChatPrefixListener proxyChatPrefixListener;
    private PlayerScoreboardService playerScoreboards;
    private FirstJoinRtpListener firstJoinRtpListener;
    private FirstJoinRtpGuardListener firstJoinRtpGuardListener;
    private FirstJoinSpawnLocationListener firstJoinSpawnLocationListener;
    private FirstJoinTrackerService firstJoinTrackerService;
    private RespawnHomeOrRtpListener respawnHomeOrRtpListener;
    private PlayerUiService playerUiService;
    private PlayerFlowService playerFlowService;
    private SleepersService sleepersService;
    private DatabaseGateway databaseGateway;

    @Override
    public String id() {
        return "core";
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(1, "core_base", "db/migration/V1__core_base.sql"),
            new MigrationScript(18, "player_flow_entry_points", "db/migration/V18__player_flow_entry_points.sql"),
            new MigrationScript(40, "sleepers_base", "db/migration/V40__sleepers_base.sql"),
            new MigrationScript(41, "sleepers_visual_and_health", "db/migration/V41__sleepers_visual_and_health.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        this.plugin = context.plugin();
        ServiceRegistry registry = context.services();

        if (!(registry instanceof SimpleServiceRegistry)) {
            throw new IllegalStateException("Foxaria core requires SimpleServiceRegistry bootstrap.");
        }

        AsyncScheduler scheduler = new PaperAsyncScheduler(plugin);
        registry.register(AsyncScheduler.class, scheduler);

        ConfigService configService = new YamlConfigService(plugin);
        configService.saveDefault("config.yml");
        configService.saveDefault("messages.yml");
        configService.saveDefault("modules/player-ui.yml");
        configService.saveDefault("modules/player-flow.yml");
        registry.register(ConfigService.class, configService);

        MessageService messageService = new YamlMessageService(plugin, configService);
        registry.register(MessageService.class, messageService);

        IntegrationService integrationService = new BukkitIntegrationService(plugin);
        registry.register(IntegrationService.class, integrationService);

        databaseGateway = new JdbcDatabaseGateway(plugin, configService, context.logger());
        databaseGateway.start();
        databaseGateway.applyMigrations(migrations());
        registry.register(DatabaseGateway.class, databaseGateway);

        AuditService auditService = new JdbcAuditService(databaseGateway);
        registry.register(AuditService.class, auditService);

        FoxariaPermissionService permissionService = new FoxariaPermissionService(plugin, configService, registry);
        registry.register(PermissionService.class, permissionService);
        registry.register(RankService.class, new PermissionBackedRankService(permissionService));

        MenuManager menuManager = new MenuManager(plugin);
        registry.register(MenuManager.class, menuManager);

        CoreRepository repository = new CoreRepository(databaseGateway, plugin);
        registry.register(CoreRepository.class, repository);

        CombatTagService combatTagService = new CombatTagService(plugin, configService, auditService);
        registry.register(CombatTagService.class, combatTagService);

        TeleportService teleportService = new TeleportService(plugin, configService, messageService, auditService, repository, combatTagService, scheduler, registry);
        registry.register(TeleportService.class, teleportService);
        teleportService.startRtpPool();

        listener = new CoreGameplayListener(configService, repository, teleportService, combatTagService, auditService);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        firstJoinTrackerService = new FirstJoinTrackerService(plugin, configService, context.logger());
        firstJoinTrackerService.start();
        registry.register(FirstJoinTrackerService.class, firstJoinTrackerService);

        firstJoinRtpListener = new FirstJoinRtpListener(plugin, configService, teleportService, firstJoinTrackerService);
        plugin.getServer().getPluginManager().registerEvents(firstJoinRtpListener, plugin);
        firstJoinRtpGuardListener = new FirstJoinRtpGuardListener(configService, firstJoinTrackerService);
        plugin.getServer().getPluginManager().registerEvents(firstJoinRtpGuardListener, plugin);
        firstJoinSpawnLocationListener = new FirstJoinSpawnLocationListener(configService, teleportService, firstJoinTrackerService);
        plugin.getServer().getPluginManager().registerEvents(firstJoinSpawnLocationListener, plugin);
        respawnHomeOrRtpListener = new RespawnHomeOrRtpListener(plugin, configService, teleportService);
        plugin.getServer().getPluginManager().registerEvents(respawnHomeOrRtpListener, plugin);

        sleepersService = new SleepersService(plugin, configService, databaseGateway, teleportService);
        sleepersService.start();

        lagProtectionService = new LagProtectionService(plugin, configService, auditService);
        lagProtectionService.start();

        playerFlowService = new PlayerFlowService(plugin, configService, messageService, registry, menuManager, repository);
        playerFlowService.start();
        registry.register(PlayerFlowService.class, playerFlowService);

        playerScoreboards = new PlayerScoreboardService(plugin);
        playerScoreboards.start();
        registry.register(PlayerScoreboardService.class, playerScoreboards);

        sidebarService = new SidebarService(plugin, configService, registry, playerScoreboards);
        sidebarService.start();

        proxyChatPrefixListener = new FoxariaProxyChatPrefixListener(plugin, permissionService, sidebarService);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "foxaria:proxy", proxyChatPrefixListener);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "foxaria:proxy");

        playerUiService = new PlayerUiService(plugin, configService, registry, messageService, menuManager);
        registry.register(PlayerUiService.class, playerUiService);
        playerUiService.start();
        registry.register(SidebarService.class, sidebarService);

        registerCommand(plugin, "spawn", new SpawnCommand(SpawnCommand.Mode.SPAWN, teleportService, messageService), null);
        registerCommand(plugin, "setspawn", new SpawnCommand(SpawnCommand.Mode.SET_SPAWN, teleportService, messageService), null);
        registerCommand(plugin, "rtp", new SpawnCommand(SpawnCommand.Mode.RTP, teleportService, messageService), null);

        HomeCommand home = new HomeCommand(HomeCommand.Mode.HOME, teleportService, messageService);
        HomeCommand sethome = new HomeCommand(HomeCommand.Mode.SET_HOME, teleportService, messageService);
        HomeCommand delhome = new HomeCommand(HomeCommand.Mode.DEL_HOME, teleportService, messageService);
        HomeCommand homes = new HomeCommand(HomeCommand.Mode.HOMES, teleportService, messageService);
        registerCommand(plugin, "home", home, home);
        registerCommand(plugin, "sethome", sethome, sethome);
        registerCommand(plugin, "delhome", delhome, delhome);
        registerCommand(plugin, "homes", homes, homes);

        TeleportRequestCommand tpa = new TeleportRequestCommand(TeleportRequestCommand.Mode.TPA, teleportService, messageService);
        TeleportRequestCommand tpaccept = new TeleportRequestCommand(TeleportRequestCommand.Mode.ACCEPT, teleportService, messageService);
        TeleportRequestCommand tpdeny = new TeleportRequestCommand(TeleportRequestCommand.Mode.DENY, teleportService, messageService);
        TeleportRequestCommand tpahere = new TeleportRequestCommand(TeleportRequestCommand.Mode.TPA_HERE, teleportService, messageService);
        registerCommand(plugin, "tpa", tpa, tpa);
        registerCommand(plugin, "tpaccept", tpaccept, tpaccept);
        registerCommand(plugin, "tpdeny", tpdeny, tpdeny);
        registerCommand(plugin, "tpahere", tpahere, tpahere);
        registerCommand(plugin, "menu", new MenuCommand(plugin, menuManager, registry, configService, messageService), null);
        registerCommand(plugin, "help", new HelpCommand(), null);
        if (configService.module("modules/player-flow.yml").getBoolean("server-selector-enabled", false)) {
            registerCommand(plugin, "server", new ServerSelectorCommand(playerFlowService, messageService), null);
        } else {
            registerCommand(plugin, "server", new ServerDisabledCommand(messageService), null);
        }
        registerCommand(plugin, "speed", new SpeedCommand(messageService), null);
        registerCommand(plugin, "permdebug", new PermDebugCommand(permissionService, messageService), null);

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (permissionService.refreshGroupIfChanged(p)) {
                    permissionService.requestChatPrefixFromProxy(p);
                    sidebarService.refresh(p);
                }
            }
        }, 40L, 40L);

        context.logger().info("Foxaria core module enabled.");
    }

    @Override
    public void stop() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (firstJoinRtpListener != null) {
            HandlerList.unregisterAll(firstJoinRtpListener);
            firstJoinRtpListener = null;
        }
        if (firstJoinRtpGuardListener != null) {
            HandlerList.unregisterAll(firstJoinRtpGuardListener);
            firstJoinRtpGuardListener = null;
        }
        if (firstJoinSpawnLocationListener != null) {
            HandlerList.unregisterAll(firstJoinSpawnLocationListener);
            firstJoinSpawnLocationListener = null;
        }
        if (respawnHomeOrRtpListener != null) {
            HandlerList.unregisterAll(respawnHomeOrRtpListener);
            respawnHomeOrRtpListener = null;
        }
        if (sleepersService != null) {
            sleepersService.stop();
            sleepersService = null;
        }
        if (lagProtectionService != null) {
            lagProtectionService.stop();
        }
        if (sidebarService != null) {
            sidebarService.stop();
        }
        if (playerScoreboards != null) {
            playerScoreboards.stop();
            playerScoreboards = null;
        }
        if (proxyChatPrefixListener != null) {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, "foxaria:proxy", proxyChatPrefixListener);
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, "foxaria:proxy");
            proxyChatPrefixListener = null;
        }
        if (playerUiService != null) {
            playerUiService.stop();
        }
        if (playerFlowService != null) {
            playerFlowService.stop();
        }
        if (databaseGateway != null) {
            databaseGateway.stop();
        }
    }

    private void registerCommand(JavaPlugin plugin, String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Command '" + name + "' is missing in plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }
}
