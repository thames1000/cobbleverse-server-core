package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.CoreConstants;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.command.CommandRegistrar;
import com.thamescape.cobbleverse.core.config.ConfigLoader;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.config.ConfigValidator;
import com.thamescape.cobbleverse.core.config.CoreConfig;
import com.thamescape.cobbleverse.core.config.DatabaseConfig;
import com.thamescape.cobbleverse.core.config.WebConfig;
import com.thamescape.cobbleverse.core.web.WebService;
import com.thamescape.cobbleverse.core.web.api.ApiData;
import com.thamescape.cobbleverse.core.web.api.ApiRouter;
import com.thamescape.cobbleverse.core.web.api.ApiServer;
import com.thamescape.cobbleverse.core.web.api.CoreApiData;
import com.thamescape.cobbleverse.core.web.webhook.WebhookDispatcher;
import com.thamescape.cobbleverse.core.web.webhook.WebhookService;
import com.thamescape.cobbleverse.core.diagnostics.ConfigHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.DatabaseHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.diagnostics.IntegrationHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.PermissionHealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.SchedulerHealthCheck;
import com.thamescape.cobbleverse.core.event.EventService;
import com.thamescape.cobbleverse.core.game.GameEventBus;
import com.thamescape.cobbleverse.core.integration.cobblemon.CobblemonGameEventAdapter;
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
import com.thamescape.cobbleverse.core.persistence.repository.StatisticsRepository;
import com.thamescape.cobbleverse.core.season.SeasonService;
import com.thamescape.cobbleverse.core.season.objective.BattleWonObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.CaptureAnyObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.CaptureShinyObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.CaptureSpeciesObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.ManualObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveRegistry;
import com.thamescape.cobbleverse.core.season.objective.SeasonObjectiveEventListener;
import com.thamescape.cobbleverse.core.statistics.StatisticsGameEventListener;
import com.thamescape.cobbleverse.core.statistics.StatisticsService;
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

        // 5c. Season system. Objective handlers register in the ObjectiveRegistry — manual plus the
        // event-driven types (0.6.1), which are driven by game events via SeasonObjectiveEventListener.
        ObjectiveRegistry objectiveRegistry = new ObjectiveRegistry();
        objectiveRegistry.register(new ManualObjectiveHandler());
        objectiveRegistry.register(new CaptureSpeciesObjectiveHandler());
        objectiveRegistry.register(new CaptureShinyObjectiveHandler());
        objectiveRegistry.register(new CaptureAnyObjectiveHandler());
        objectiveRegistry.register(new BattleWonObjectiveHandler());
        // Register the objective-type check as a semantic validator so it runs both now (startup) and on
        // every /cvcore reload — deferred from load-time validation so custom registry types are honoured
        // rather than rejected as "unknown", but never allowed to silently degrade on a later reload.
        configManager.addSemanticValidator(snapshot ->
                ConfigValidator.validateObjectiveTypes(snapshot.seasons(), objectiveRegistry.types()));
        configManager.validateSemanticsOrThrow("CV-CONFIG-017");
        SeasonService seasonService = new SeasonService(configManager, databaseManager,
                new SeasonRepository(), rewardService, auditService, objectiveRegistry);
        seasonService.checkLifecycle(); // record initial lifecycle state on boot
        seasonService.resumePendingMilestones(); // re-deliver milestone rewards a crash left pending

        // 5d. Event system (admin-driven lifecycle in 0.5.0).
        EventService eventService = new EventService(configManager, databaseManager,
                new EventRepository(), rewardService, auditService);
        eventService.resumePendingDistributions(); // resume any reward distribution a crash interrupted

        // 5e. Game-event ingestion bus (producers publish; consumers subscribe — 0.6.0).
        GameEventBus gameEventBus = new GameEventBus();
        // Cobblemon is optional: only touch the adapter (which imports Cobblemon classes) when the mod
        // is actually installed, so the core loads and runs standalone without it.
        boolean cobblemonBridgeActive = FabricLoader.getInstance().isModLoaded("cobblemon");
        if (cobblemonBridgeActive) {
            new CobblemonGameEventAdapter(gameEventBus).register();
        } else {
            LOGGER.info("Cobblemon not present; game-event bridge idle");
        }

        // 5f. Bus consumers (0.6.1): season objective auto-progress and player statistics.
        StatisticsService statisticsService = new StatisticsService(databaseManager, new StatisticsRepository());
        gameEventBus.register(new SeasonObjectiveEventListener(seasonService));
        gameEventBus.register(new StatisticsGameEventListener(statisticsService));

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

        // 7b. Web integration (0.7.0): read-only HTTP API + outbound webhooks. Both off by default; each
        // is built only when its config section is enabled. The API binds on server start (step 8).
        WebService webService = buildWebService(configManager.web(), healthCheckService, seasonService,
                eventService, statisticsService, playerService, auditService);

        // 8. Track the running server, and bind/unbind the web API alongside its lifecycle.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerHolder.set(server);
            webService.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            webService.stop();
            ServerHolder.clear();
        });

        // 9. Publish the service registry.
        ServiceRegistry registry = new ServiceRegistry(
                configManager, permissionService, messageService, integrationManager,
                auditService, healthCheckService, databaseManager, playerService, scheduler,
                rewardService, currencyService, seasonService, eventService, gameEventBus,
                statisticsService, webService);
        CoreServices.install(registry);

        // 10. Register commands and player lifecycle.
        CommandRegistrar.register();
        new PlayerLifecycleListener(playerService, rewardService, gameEventBus).register();

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

    /**
     * Builds the web-integration services. The API server is created only when {@code api.enabled}; the
     * webhook service only when {@code webhooks.enabled}, in which case it attaches to the audit stream
     * here. Either may be null inside the returned {@link WebService}.
     */
    private static WebService buildWebService(WebConfig web, HealthCheckService health, SeasonService seasons,
                                              EventService events, StatisticsService statistics,
                                              PlayerProfileService players, AuditService audit) {
        ApiServer apiServer = null;
        if (web.api.enabled) {
            ApiData data = new CoreApiData(CoreBootstrap::version, health, seasons, events, statistics, players);
            ApiRouter router = new ApiRouter(data, web.api.apiKey, web.api.leaderboardMaxLimit,
                    web.api.maxConcurrentRequests, web.api.rateLimitPerMinute);
            // A few more handler threads than the concurrency permit, so overflow requests can still be
            // answered promptly with 503 instead of waiting for a permit-holding thread.
            int threads = web.api.maxConcurrentRequests + 4;
            apiServer = new ApiServer(web.api.bindAddress, web.api.port, threads, router);
        }
        WebhookService webhookService = null;
        if (web.webhooks.enabled) {
            WebhookDispatcher dispatcher =
                    new WebhookDispatcher(web.webhooks.timeoutSeconds, web.webhooks.maxRetries);
            webhookService = new WebhookService(web.webhooks, dispatcher);
            audit.addListener(webhookService::onAudit);
        }
        return new WebService(web, apiServer, webhookService);
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
