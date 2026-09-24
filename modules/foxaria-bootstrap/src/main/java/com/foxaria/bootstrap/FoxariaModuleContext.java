package com.foxaria.bootstrap;

import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.AsyncScheduler;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.DatabaseGateway;
import com.foxaria.api.service.IntegrationService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.PermissionService;
import com.foxaria.api.service.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Logger;

public record FoxariaModuleContext(JavaPlugin plugin, ServiceRegistry services) implements ModuleContext {

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }

    @Override
    public Path dataPath() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public AsyncScheduler scheduler() {
        return services.require(AsyncScheduler.class);
    }

    @Override
    public ConfigService configs() {
        return services.require(ConfigService.class);
    }

    @Override
    public MessageService messages() {
        return services.require(MessageService.class);
    }

    @Override
    public PermissionService permissions() {
        return services.require(PermissionService.class);
    }

    @Override
    public IntegrationService integrations() {
        return services.require(IntegrationService.class);
    }

    @Override
    public DatabaseGateway database() {
        return services.require(DatabaseGateway.class);
    }

    @Override
    public AuditService audits() {
        return services.require(AuditService.class);
    }
}
