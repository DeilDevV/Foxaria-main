package com.foxaria.cases.bukkit;

import com.foxaria.cases.CasesApplication;
import com.foxaria.cases.listener.CaseProxyInboundListener;
import com.foxaria.cases.service.CaseProxyBridge;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.service.JdbcAuditService;
import com.foxaria.core.service.JdbcDatabaseGateway;
import com.foxaria.core.service.YamlConfigService;
import com.foxaria.core.service.YamlMessageService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class FoxariaCasesPlugin extends JavaPlugin {

    private YamlConfigService configService;
    private JdbcDatabaseGateway database;
    private MenuManager menuManager;
    private CasesApplication application;
    private CaseProxyInboundListener proxyInboundListener;

    @Override
    public void onLoad() {
        FoxariaCasesConfigBootstrap.ensureDefaults(this);
    }

    @Override
    public void onEnable() {
        try {
            configService = new YamlConfigService(this);
            YamlMessageService messages = new YamlMessageService(this, configService);

            database = new JdbcDatabaseGateway(this, configService, getLogger());
            database.start();

            menuManager = new MenuManager(this);
            getServer().getPluginManager().registerEvents(menuManager, this);

            proxyInboundListener = new CaseProxyInboundListener(this);
            getServer().getMessenger().registerIncomingPluginChannel(this, CaseProxyBridge.CHANNEL, proxyInboundListener);

            application = new CasesApplication(
                this,
                menuManager,
                messages,
                new JdbcAuditService(database),
                new CasesApplication.CaseDatabase() {
                    @Override
                    public com.foxaria.api.service.DatabaseGateway gateway() {
                        return database;
                    }

                    @Override
                    public void applyMigrations(java.util.List<com.foxaria.api.MigrationScript> migrations) {
                        database.applyMigrations(migrations);
                    }
                }
            );
            application.start(casesConfig());

            getLogger().info("FoxariaCases enabled. Config folder: " + getDataFolder().getAbsolutePath());
        } catch (Exception exception) {
            getLogger().severe("FoxariaCases failed to start. Check " + getDataFolder().getAbsolutePath() + "/config.yml (database section).");
            getLogger().severe("Error: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (application != null) {
            application.stop();
        }
        if (menuManager != null) {
            HandlerList.unregisterAll(menuManager);
        }
        if (proxyInboundListener != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, CaseProxyBridge.CHANNEL, proxyInboundListener);
        }
        if (database != null) {
            database.stop();
        }
    }

    public void reloadCases() {
        reloadConfig();
        configService = new YamlConfigService(this);
        application.reload(casesConfig());
    }

    private FileConfiguration casesConfig() {
        File file = new File(getDataFolder(), "cases.yml");
        if (!file.exists()) {
            saveResource("cases.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
