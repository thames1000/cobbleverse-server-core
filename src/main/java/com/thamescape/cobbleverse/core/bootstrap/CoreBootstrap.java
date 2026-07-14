package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.CoreConstants;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.command.CommandRegistrar;
import com.thamescape.cobbleverse.core.config.ConfigLoader;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.config.CoreConfig;
import com.thamescape.cobbleverse.core.config.DatabaseConfig;
import com.thamescape.cobbleverse.core.diagnostics.ConfigHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.DatabaseHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.diagnostics.IntegrationHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.PermissionHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.SchedulerHealthCheck;
import com.thamescape.cobbleverse.core.integration.IntegrationManager;
import com.thamescape.cobbleverse.core.integration.IntegrationReport;
import com.thamescape.cobbleverse.core.message.MessageService;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.AuditRepository;
import com.thamescape.cobbleverse.core.persistence.repository.PlayerProfileRepository;
import com.thamescape.cobbleverse.core.player.PlayerLifecycleListener;
import com.thamescape.cobbleverse.core.player.PlayerProfileService;
import com.thamescape.cobbleverse.core.scheduler.CoreScheduler;
import com.thamescape.cobbleverse.core.scheduler.tasks.DatabaseFlushTask;
import com.thamescape.cobbleverse.core.scheduler.tasks.PlaytimeUpdateTask;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Initializes every core system in a predictable order and wires them into the
 * {@link ServiceRegistry}. Fatal problems (missing deps, invalid config, unopenable database) abort
 * startup with a clear message rather than leaving the core half-initialized.
 *
 * <p>Startup order:
 * <ol>
 *   <li>Validate required mods</li>
 *   <li>Load and validate configuration (core + database)</li>
 *   <li>Build permission / message services</li>
 *   <li>Open the database and run migrations (backup taken first if migrating an existing db)</li>
 *   <li>Build repositories, audit and player-profile services</li>
 *   <li>Start the scheduler and its tasks</li>
 *   <li>Register + detect integrations</li>
 *   <li>Register health checks</li>
 *   <li>Publish the service registry</li>
 *   <li>Register commands and the player lifecycle listener</li>
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
        DatabaseConfig dbConfig = configManager.database();

        // 3. Build permission and message services.
        PermissionService permissionService = new PermissionService();
        MessageService messageService = new MessageService(configLoader);
        messageService.load();

        // 4. Open the database and migrate.
        DatabaseManager databaseManager = openDatabase(configLoader.configDir(), dbConfig);

        // 5. Repositories and services that depend on the database.
        PlayerProfileRepository playerRepository = new PlayerProfileRepository();
        AuditRepository auditRepository = new AuditRepository();
        AuditService auditService = new AuditService(core.enableAuditLog, databaseManager, auditRepository);
        PlayerProfileService playerService = new PlayerProfileService(databaseManager, playerRepository);

        // 6. Scheduler + periodic tasks.
        CoreScheduler scheduler = new CoreScheduler();
        scheduler.init();
        scheduler.scheduleRepeating(PlaytimeUpdateTask.ID,
                (long) dbConfig.playtimeAccrualSeconds * CoreScheduler.TICKS_PER_SECOND,
                new PlaytimeUpdateTask(playerService));
        scheduler.scheduleRepeating(DatabaseFlushTask.ID,
                (long) dbConfig.flushIntervalSeconds * CoreScheduler.TICKS_PER_SECOND,
                new DatabaseFlushTask(playerService));

        // 7. Register and detect integrations.
        IntegrationManager integrationManager = new IntegrationManager();
        integrationManager.registerDefaults();
        List<IntegrationReport> reports = integrationManager.detectAll();

        // 8. Register health checks.
        HealthCheckService healthCheckService = new HealthCheckService();
        healthCheckService.register(new ConfigHealthCheck(configManager));
        healthCheckService.register(new DatabaseHealthCheck(databaseManager));
        healthCheckService.register(new SchedulerHealthCheck(scheduler));
        healthCheckService.register(new PermissionHealthCheck(integrationManager));
        healthCheckService.register(new IntegrationHealthCheck(integrationManager));

        // 9. Publish the service registry.
        ServiceRegistry registry = new ServiceRegistry(
                configManager, permissionService, messageService, integrationManager,
                auditService, healthCheckService, databaseManager, playerService, scheduler);
        CoreServices.install(registry);

        // 10. Register commands and player lifecycle.
        CommandRegistrar.register();
        new PlayerLifecycleListener(playerService).register();

        // 11. Print the startup report.
        new StartupReport(version(), databaseManager.describe(), core.activeSeason, false, reports).print();

        return registry;
    }

    private static DatabaseManager openDatabase(Path configDir, DatabaseConfig dbConfig) {
        Path dbFile = configDir.resolve(dbConfig.fileName);
        boolean existedBefore = Files.exists(dbFile);

        DatabaseManager databaseManager = new DatabaseManager(new SqliteDatabaseProvider(dbFile));
        databaseManager.init();

        MigrationManager migrations = MigrationManager.withDefaults();
        int current = migrations.currentVersion(databaseManager);
        if (existedBefore && current < migrations.latestVersion()) {
            backupBeforeMigration(databaseManager, dbFile, current);
        }
        migrations.migrate(databaseManager);
        return databaseManager;
    }

    private static void backupBeforeMigration(DatabaseManager db, Path dbFile, int fromVersion) {
        try {
            db.checkpoint();
            Path backup = dbFile.resolveSibling(dbFile.getFileName() + ".v" + fromVersion + ".bak");
            Files.copy(dbFile, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Backed up database (schema v{}) to {} before migrating",
                    fromVersion, backup.getFileName());
        } catch (IOException e) {
            LOGGER.warn("Could not back up database before migration: {}", e.getMessage());
        }
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer(CoreConstants.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
