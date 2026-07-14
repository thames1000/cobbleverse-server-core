package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.CoreConstants;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.command.CommandRegistrar;
import com.thamescape.cobbleverse.core.config.ConfigLoader;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.config.CoreConfig;
import com.thamescape.cobbleverse.core.diagnostics.ConfigHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.diagnostics.IntegrationHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.PermissionHealthCheck;
import com.thamescape.cobbleverse.core.integration.IntegrationManager;
import com.thamescape.cobbleverse.core.integration.IntegrationReport;
import com.thamescape.cobbleverse.core.message.MessageService;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Initializes every core system in a predictable order and wires them into the
 * {@link ServiceRegistry}. Fatal problems (missing deps, invalid config) abort startup with a clear
 * message rather than leaving the core half-initialized.
 *
 * <p>Startup order:
 * <ol>
 *   <li>Validate required mods</li>
 *   <li>Load and validate configuration</li>
 *   <li>Build services (permissions, messages, audit)</li>
 *   <li>Register + detect integrations</li>
 *   <li>Register health checks</li>
 *   <li>Publish the service registry</li>
 *   <li>Register commands</li>
 *   <li>Print the startup report</li>
 * </ol>
 */
public final class CoreBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CORE");

    private CoreBootstrap() {
    }

    /** Runs the full startup sequence. Returns the populated registry, or throws on fatal error. */
    public static ServiceRegistry run() {
        // 1. Validate required mods.
        List<String> missing = DependencyValidator.validate();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required dependencies: " + String.join(", ", missing));
        }

        // 2. Load and validate configuration.
        ConfigLoader configLoader = new ConfigLoader();
        ConfigManager configManager = new ConfigManager(configLoader);
        configManager.load();
        CoreConfig core = configManager.core();

        // 3. Build services.
        PermissionService permissionService = new PermissionService();
        MessageService messageService = new MessageService(configLoader);
        messageService.load();
        AuditService auditService = new AuditService(core.enableAuditLog);

        // 4. Register and detect integrations.
        IntegrationManager integrationManager = new IntegrationManager();
        integrationManager.registerDefaults();
        List<IntegrationReport> reports = integrationManager.detectAll();

        // 5. Register health checks.
        HealthCheckService healthCheckService = new HealthCheckService();
        healthCheckService.register(new ConfigHealthCheck(configManager));
        healthCheckService.register(new PermissionHealthCheck(integrationManager));
        healthCheckService.register(new IntegrationHealthCheck(integrationManager));

        // 6. Publish the service registry.
        ServiceRegistry registry = new ServiceRegistry(
                configManager, permissionService, messageService,
                integrationManager, auditService, healthCheckService);
        CoreServices.install(registry);

        // 7. Register commands.
        CommandRegistrar.register();

        // 8. Print the startup report.
        new StartupReport(version(), "none (in-memory, 0.1.0)", core.activeSeason, false, reports).print();

        return registry;
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer(CoreConstants.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
