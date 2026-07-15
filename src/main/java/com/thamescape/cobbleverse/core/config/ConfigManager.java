package com.thamescape.cobbleverse.core.config;

import com.thamescape.cobbleverse.core.util.error.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the live configuration and mediates loads and safe reloads.
 *
 * <p>All configuration is held as one immutable {@link ConfigSnapshot} behind a single
 * {@code volatile} reference, so publication is atomic: a reader (including off-thread database/reward
 * work) always sees one coherent generation, never a mix of new and old files mid-reload.
 *
 * <p>Only {@code core.json}, {@code rewards.json}, {@code seasons.json} and {@code events.json} are
 * runtime-reloadable. {@code database.json} is fixed at startup (changing the driver/file needs a
 * restart), so a reload carries the live database config forward unchanged.
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CONFIG");
    private static final String CORE_FILE = "core.json";
    private static final String DATABASE_FILE = "database.json";
    private static final String REWARDS_FILE = "rewards.json";
    private static final String SEASONS_FILE = "seasons.json";
    private static final String EVENTS_FILE = "events.json";
    private static final String WEB_FILE = "web.json";

    private final ConfigLoader loader;
    private final List<SnapshotValidator> semanticValidators = new CopyOnWriteArrayList<>();
    private volatile ConfigSnapshot live;

    public ConfigManager(ConfigLoader loader) {
        this.loader = loader;
    }

    /**
     * Registers a semantic validator that runs — in addition to structural validation — against every
     * candidate generation before it is published, both at startup ({@link #validateSemanticsOrThrow})
     * and on every {@link #reload()}. Lets a subsystem reject config that structural validation cannot
     * judge (e.g. an objective type with no registered handler), so a reload can't silently reintroduce
     * a broken generation. Validators are typically registered once the runtime state they consult
     * (registries, providers) exists.
     */
    public void addSemanticValidator(SnapshotValidator validator) {
        semanticValidators.add(validator);
    }

    /**
     * Runs every registered semantic validator against the live snapshot and throws if any reports a
     * problem. Called at startup once subsystems have registered their validators, so semantic breakage
     * fails fast exactly as structural {@link #load()} validation does.
     */
    public void validateSemanticsOrThrow(String code) {
        throwIfInvalid(code, "configuration semantics", runSemanticValidators(live()));
    }

    private List<String> runSemanticValidators(ConfigSnapshot candidate) {
        List<String> problems = new ArrayList<>();
        for (SnapshotValidator validator : semanticValidators) {
            problems.addAll(validator.validate(candidate));
        }
        return problems;
    }

    /** Loads and validates all config from disk, then publishes it as one snapshot. Throws if invalid. */
    public void load() {
        CoreConfig core = loader.loadOrCreate(CORE_FILE, CoreConfig.class, CoreConfig::defaults);
        throwIfInvalid("CV-CONFIG-010", "core.json", ConfigValidator.validate(core));

        DatabaseConfig database = loader.loadOrCreate(DATABASE_FILE, DatabaseConfig.class, DatabaseConfig::defaults);
        throwIfInvalid("CV-CONFIG-012", "database.json", ConfigValidator.validate(database));

        RewardsConfig rewards = loader.loadOrCreate(REWARDS_FILE, RewardsConfig.class, RewardsConfig::defaults);
        throwIfInvalid("CV-CONFIG-014", "rewards.json", ConfigValidator.validate(rewards));

        SeasonsConfig seasons = loader.loadOrCreate(SEASONS_FILE, SeasonsConfig.class, SeasonsConfig::defaults);
        throwIfInvalid("CV-CONFIG-016", "seasons.json", ConfigValidator.validate(seasons));

        EventsConfig events = loader.loadOrCreate(EVENTS_FILE, EventsConfig.class, EventsConfig::defaults);
        throwIfInvalid("CV-CONFIG-018", "events.json", ConfigValidator.validate(events));

        WebConfig web = loader.loadOrCreate(WEB_FILE, WebConfig.class, WebConfig::defaults);
        throwIfInvalid("CV-CONFIG-022", "web.json", ConfigValidator.validate(web));

        throwIfInvalid("CV-CONFIG-020", "cross-config references",
                ConfigValidator.validateCrossReferences(rewards, seasons, events));

        backfillIds(rewards, seasons, events);
        this.live = new ConfigSnapshot(core, database, rewards, seasons, events, web);
        LOGGER.info("Loaded configuration (serverId={}, environment={})", core.serverId, core.environment);
    }

    /**
     * Re-reads the runtime-reloadable files, validates the whole candidate generation (every file plus
     * cross-references), and — only if all valid — publishes it in a single atomic assignment. On any
     * problem, nothing changes (the previous snapshot stays live in full) and the problems are returned.
     *
     * @return list of validation problems; empty on success
     */
    public List<String> reload() {
        ConfigSnapshot current = live();

        CoreConfig core = loader.loadOrCreate(CORE_FILE, CoreConfig.class, CoreConfig::defaults);
        RewardsConfig rewards = loader.loadOrCreate(REWARDS_FILE, RewardsConfig.class, RewardsConfig::defaults);
        SeasonsConfig seasons = loader.loadOrCreate(SEASONS_FILE, SeasonsConfig.class, SeasonsConfig::defaults);
        EventsConfig events = loader.loadOrCreate(EVENTS_FILE, EventsConfig.class, EventsConfig::defaults);

        List<String> problems = new ArrayList<>();
        problems.addAll(ConfigValidator.validate(core));
        problems.addAll(ConfigValidator.validate(rewards));
        problems.addAll(ConfigValidator.validate(seasons));
        problems.addAll(ConfigValidator.validate(events));
        if (problems.isEmpty()) {
            problems.addAll(ConfigValidator.validateCrossReferences(rewards, seasons, events));
        }
        if (problems.isEmpty()) {
            // Structural checks passed. Backfill ids, assemble the candidate generation, and let the
            // semantic validators (e.g. objective types vs the live registry) judge it before publish —
            // otherwise a reload could reintroduce the silent-degradation the startup check prevents.
            backfillIds(rewards, seasons, events);
            ConfigSnapshot candidate = new ConfigSnapshot(core, current.database(), rewards, seasons,
                    events, current.web());
            problems.addAll(runSemanticValidators(candidate));
            if (problems.isEmpty()) {
                // Single atomic publication: one volatile write swaps the entire config generation.
                this.live = candidate;
                LOGGER.info("Reloaded configuration (rewards={}, seasons={}, events={})",
                        rewards.definitions.size(), seasons.seasons.size(), events.events.size());
                return problems;
            }
        }
        LOGGER.warn("Reload rejected; keeping previous config in full. {} problem(s).", problems.size());
        return problems;
    }

    /** The live snapshot. All accessors read through this so they see one coherent generation. */
    public ConfigSnapshot snapshot() {
        return live();
    }

    public CoreConfig core() {
        return live().core();
    }

    /**
     * The database config. Fixed at startup and <b>not</b> runtime-reloadable — a reload carries it
     * forward unchanged; changing the driver or file requires a restart.
     */
    public DatabaseConfig database() {
        return live().database();
    }

    /** The reward definitions and currency/template config. Runtime-reloadable. */
    public RewardsConfig rewards() {
        return live().rewards();
    }

    /** The season definitions. Runtime-reloadable. */
    public SeasonsConfig seasons() {
        return live().seasons();
    }

    /** The event definitions. Runtime-reloadable. */
    public EventsConfig events() {
        return live().events();
    }

    /**
     * The web-integration config (API + webhooks). Fixed at startup and <b>not</b> runtime-reloadable —
     * a reload carries it forward unchanged; changing the bound port or subscriptions requires a restart.
     */
    public WebConfig web() {
        return live().web();
    }

    public ConfigLoader loader() {
        return loader;
    }

    private ConfigSnapshot live() {
        ConfigSnapshot current = live;
        if (current == null) {
            throw new ConfigurationException("CV-CONFIG-011", "Configuration accessed before load()");
        }
        return current;
    }

    private static void throwIfInvalid(String code, String what, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new ConfigurationException(code, "Invalid " + what + ":\n  - " + String.join("\n  - ", problems));
        }
    }

    private static void backfillIds(RewardsConfig rewards, SeasonsConfig seasons, EventsConfig events) {
        // Ids are the map keys, not stored in each JSON body.
        rewards.definitions.forEach((id, def) -> def.id = id);
        seasons.seasons.forEach((id, def) -> def.id = id);
        events.events.forEach((id, def) -> def.id = id);
    }
}
