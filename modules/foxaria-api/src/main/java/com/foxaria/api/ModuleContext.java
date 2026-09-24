package com.foxaria.api;

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

public interface ModuleContext {

    JavaPlugin plugin();

    Logger logger();

    Path dataPath();

    ServiceRegistry services();

    AsyncScheduler scheduler();

    ConfigService configs();

    MessageService messages();

    PermissionService permissions();

    IntegrationService integrations();

    DatabaseGateway database();

    AuditService audits();
}
