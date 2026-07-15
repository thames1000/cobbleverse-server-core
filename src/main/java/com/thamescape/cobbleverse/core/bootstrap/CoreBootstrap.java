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
import com.thamescape.cobbleverse.core.event.EventService;
import com.thamescape.cobbleverse.core.integration.IntegrationManager;
import com.thamescape.cobbleverse.core.integration.IntegrationReport;
import com.thamescape.cobbleverse.core.message.MessageService;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.AuditRepository;
import com.thamescape.cobbleverse.core.persistence.repository.CurrencyRepository;
import com.thamescape.cobbleverse.core.persistence.repository.EventRepository;
import com.thamescape.cobbleverse.core.persistence.repository.PlayerProfileRepository;
import com.thamescape.cobbleverse.core.persistence.repository.RewardRepository;
import com.thamescape.cobbleverse.core.player.PlayerLifecycleListener;
import com.thamescape.cobbleverse.core.player.PlayerProfileService;
import com.thamescape.cobbleverse.core.reward.RewardRegistry;
import com.thamescape.cobbleverse.core.reward.RewardService;
import com.thamescape.cobbleverse.core.reward.RewardType;
import com.thamescape.cobbleverse.core.reward.currency.CommandCurrencyProvider;
import com.thamescape.cobbleverse.core.reward.currency.CurrencyService;
import com.thamescape.cobbleverse.core.reward.currency.InternalCurrencyProvider;
import com.thamescape.cobbleverse.core.reward.type.CommandRewardHandler;
import com.thamescape.cobbleverse.core.reward.type.CommandTemplateRewardHandler;
import com.thamescape.cobbleverse.core.reward.type.CurrencyRewardHandler;
import com.thamescape.cobbleverse.core.reward.type.ItemRewardHandler;
import com.thamescape.cobbleverse.core.persistence.repository.SeasonRepository;
import com.thamescape.cobbleverse.core.scheduler.CoreScheduler;
import com.thamescape.cobbleverse.core.scheduler.tasks.DatabaseFlushTask;
import com.thamescape.cobbleverse.core.scheduler.tasks.PlaytimeUpdateTask;
import com.thamescape.cobbleverse.core.scheduler.tasks.SeasonLifecycleTask;
import com.thamescape.cobbleverse.core.season.SeasonService;
import com.thamescape.cobbleverse.core.season.objective.ManualObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveRegistry;
import com.thamescape.cobbleverse.core.util.ServerHolder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

        // 5b. Currency + reward systems.
        IntegrationManager integrationManager = new IntegrationManager();
        integrationManager.registerDefaults();
        List<IntegrationReport> reports = integrationManager.detectAll();

        CurrencyService currencyService = buildCurrencyService(configManager, databaseManager,
                auditService, integrationManager);
        RewardService rewardService = buildRewardService(configManager, databaseManager,
                auditService, currencyService);

        // 5c. Season system. Objective handlers register in the ObjectiveRegistry (manual only in 0.4.0).
        ObjectiveRegistry objectiveRegistry = new ObjectiveRegistry();
        objectiveRegistry.register(new ManualObjectiveHandler());
        SeasonService seasonService = new SeasonService(configManager, databaseManager,
                new SeasonRepository(), rewardService, auditService, objectiveRegistry);
        seasonService.checkLifecycle(); // record initial lifecycle state on boot

        // 5d. Event system (admin-driven lifecycle in 0.5.0).
        EventService eventService = new EventService(configManager, databaseManager,
                new EventRepository(), rewardService, auditService);
        eventService.resumePendingDistributions(); // resume any reward distribution a crash interrupted

        // 6. Scheduler + periodic tasks.
        CoreScheduler scheduler = new CoreScheduler();
        scheduler.init();
        scheduler.scheduleRepeating(PlaytimeUpdateTask.ID,
                (long) dbConfig.playtimeAccrualSeconds * CoreScheduler.TICKS_PER_SECOND,
                new PlaytimeUpdateTask(playerService));
        scheduler.scheduleRepeating(DatabaseFlushTask.ID,
                (long) dbConfig.flushIntervalSeconds * CoreScheduler.TICKS_PER_SECOND,
                new DatabaseFlushTask(playerService));
        scheduler.scheduleRepeating(SeasonLifecycleTask.ID,
                60L * CoreScheduler.TICKS_PER_SECOND, // check season transitions once a minute
                new SeasonLifecycleTask(seasonService));

        // 7. Register health checks.
        HealthCheckService healthCheckService = new HealthCheckService();
        healthCheckService.register(new ConfigHealthCheck(configManager));
        healthCheckService.register(new DatabaseHealthCheck(databaseManager));
        healthCheckService.register(new SchedulerHealthCheck(scheduler));
        healthCheckService.register(new PermissionHealthCheck(integrationManager));
        healthCheckService.register(new IntegrationHealthCheck(integrationManager));

        // 8. Track the running server so components built here can reach it later.
        ServerLifecycleEvents.SERVER_STARTED.register(ServerHolder::set);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerHolder.clear());

        // 9. Publish the service registry.
        ServiceRegistry registry = new ServiceRegistry(
                configManager, permissionService, messageService, integrationManager,
                auditService, healthCheckService, databaseManager, playerService, scheduler,
                rewardService, currencyService, seasonService, eventService);
        CoreServices.install(registry);

        // 10. Register commands and player lifecycle.
        CommandRegistrar.register();
        new PlayerLifecycleListener(playerService, rewardService).register();

        // 11. Print the startup report.
        new StartupReport(version(), databaseManager.describe(), core.activeSeason, false, reports).print();

        return registry;
    }

    private static CurrencyService buildCurrencyService(ConfigManager config, DatabaseManager db,
                                                        AuditService audit, IntegrationManager integrations) {
        CurrencyRepository currencyRepository = new CurrencyRepository();
        CurrencyService currencyService = new CurrencyService(audit);
        for (String currencyId : config.rewards().internalCurrencies) {
            currencyService.register(new InternalCurrencyProvider(currencyId, db, currencyRepository));
        }
        if (integrations.isAvailable("cobbledollars")) {
            currencyService.register(new CommandCurrencyProvider("cobbledollars",
                    () -> config.rewards().templates.cobbledollarsDeposit,
                    () -> config.rewards().templates.cobbledollarsWithdraw));
        }
        return currencyService;
    }

    private static RewardService buildRewardService(ConfigManager config, DatabaseManager db,
                                                    AuditService audit, CurrencyService currencies) {
        RewardRegistry registry = new RewardRegistry();
        registry.register(new ItemRewardHandler());
        registry.register(new CommandRewardHandler());
        registry.register(new CurrencyRewardHandler(currencies));
        registry.register(new CommandTemplateRewardHandler(RewardType.CRATE_KEY,
                () -> config.rewards().templates.crateKey, "key",
                e -> e.key, e -> e.amount + "x crate key '" + e.key + "'"));
        registry.register(new CommandTemplateRewardHandler(RewardType.PERMISSION,
                () -> config.rewards().templates.permission, "node",
                e -> e.node, e -> "permission " + e.node));
        registry.register(new CommandTemplateRewardHandler(RewardType.POKEMON,
                () -> config.rewards().templates.pokemon, "value",
                e -> e.value, e -> "pokemon " + e.value));
        registry.register(new CommandTemplateRewardHandler(RewardType.COSMETIC,
                () -> config.rewards().templates.cosmetic, "value",
                e -> e.value, e -> "cosmetic " + e.value));
        return new RewardService(config, db, new RewardRepository(), registry, audit);
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
