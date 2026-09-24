package com.foxaria.cases;

import com.foxaria.api.MigrationScript;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.MessageService;
import com.foxaria.cases.command.CaseAdminCommand;
import com.foxaria.cases.command.CaseCommand;
import com.foxaria.cases.listener.CaseBlockListener;
import com.foxaria.cases.service.CaseAnimationService;
import com.foxaria.cases.service.CaseHologramService;
import com.foxaria.cases.service.CaseItemService;
import com.foxaria.cases.service.CaseOpenLockService;
import com.foxaria.cases.service.CaseProxyBridge;
import com.foxaria.cases.service.CaseRewardExecutor;
import com.foxaria.cases.service.CaseService;
import com.foxaria.cases.service.ConfigCaseService;
import com.foxaria.cases.service.PlacedCaseService;
import com.foxaria.core.gui.MenuManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Общий запуск логики кейсов — используется и в Foxaria-модуле, и в отдельном плагине FoxariaCases.
 */
public final class CasesApplication {

    private static final List<MigrationScript> MIGRATIONS = List.of(
        new MigrationScript(42, "cases_base", "db/migration/V42__cases.sql")
    );

    private final JavaPlugin plugin;
    private final MenuManager menuManager;
    private final MessageService messages;
    private final AuditService audits;
    private final CaseDatabase database;

    private ConfigCaseService config;
    private CaseProxyBridge proxyBridge;
    private Listener blockListener;
    private CaseService caseService;

    public CasesApplication(
        JavaPlugin plugin,
        MenuManager menuManager,
        MessageService messages,
        AuditService audits,
        CaseDatabase database
    ) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.messages = messages;
        this.audits = audits;
        this.database = database;
    }

    public void start(FileConfiguration casesConfig) {
        database.applyMigrations(MIGRATIONS);

        config = new ConfigCaseService(casesConfig);
        CaseRepository repository = new CaseRepository(database.gateway());
        CaseItemService items = new CaseItemService(plugin);
        CaseHologramService holograms = new CaseHologramService(config);
        PlacedCaseService placedCases = new PlacedCaseService(plugin, config, repository, items, holograms);
        CaseOpenLockService locks = new CaseOpenLockService();
        CaseAnimationService animation = new CaseAnimationService(plugin, config, items, placedCases);
        proxyBridge = new CaseProxyBridge(plugin);
        proxyBridge.register();
        CaseRewardExecutor rewardExecutor = new CaseRewardExecutor(plugin, config, repository, proxyBridge);
        caseService = new CaseService(
            plugin,
            config,
            repository,
            items,
            placedCases,
            locks,
            animation,
            rewardExecutor,
            menuManager,
            messages,
            audits
        );

        blockListener = new CaseBlockListener(caseService, items, placedCases, messages);
        plugin.getServer().getPluginManager().registerEvents(blockListener, plugin);
        placedCases.restoreAll();

        registerCommands();
    }

    public void stop() {
        if (blockListener != null) {
            HandlerList.unregisterAll(blockListener);
        }
        if (proxyBridge != null) {
            proxyBridge.unregister();
        }
    }

    public void reload(FileConfiguration casesConfig) {
        config.reload(casesConfig);
        if (caseService != null) {
            caseService.reload();
        }
    }

    public CaseService caseService() {
        return caseService;
    }

    public ConfigCaseService config() {
        return config;
    }

    private void registerCommands() {
        PluginCommand caseCmd = plugin.getCommand("case");
        if (caseCmd != null) {
            caseCmd.setExecutor(new CaseCommand(messages));
        }
        PluginCommand caseAdmin = plugin.getCommand("caseadmin");
        if (caseAdmin != null) {
            CaseAdminCommand admin = new CaseAdminCommand(caseService, config, plugin, messages);
            caseAdmin.setExecutor(admin);
            caseAdmin.setTabCompleter(admin);
        }
    }

    public interface CaseDatabase {
        com.foxaria.api.service.DatabaseGateway gateway();

        void applyMigrations(List<MigrationScript> migrations);
    }
}
