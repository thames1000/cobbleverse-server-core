package com.thamescape.cobbleverse.core.config;

import com.thamescape.cobbleverse.core.util.error.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Owns the live core configuration and mediates loads and safe reloads.
 *
 * <p>Only configuration that is safe to change at runtime is reloadable. Registries, world data,
 * database drivers and mixins are never touched here.
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CONFIG");
    private static final String CORE_FILE = "core.json";
    private static final String DATABASE_FILE = "database.json";
    private static final String REWARDS_FILE = "rewards.json";
    private static final String SEASONS_FILE = "seasons.json";
    private static final String EVENTS_FILE = "events.json";

    private final ConfigLoader loader;
    private volatile CoreConfig coreConfig;
    private volatile DatabaseConfig databaseConfig;
    private volatile RewardsConfig rewardsConfig;
    private volatile SeasonsConfig seasonsConfig;
    private volatile EventsConfig eventsConfig;

    public ConfigManager(ConfigLoader loader) {
        this.loader = loader;
    }

    /** Loads and validates all config from disk. Throws if anything is invalid. */
    public void load() {
        CoreConfig loaded = loader.loadOrCreate(CORE_FILE, CoreConfig.class, CoreConfig::defaults);
        List<String> problems = ConfigValidator.validate(loaded);
        if (!problems.isEmpty()) {
            throw new ConfigurationException("CV-CONFIG-010",
                    "Invalid core.json:\n  - " + String.join("\n  - ", problems));
        }
        this.coreConfig = loaded;
        LOGGER.info("Loaded core configuration (serverId={}, environment={})",
                loaded.serverId, loaded.environment);

        DatabaseConfig db = loader.loadOrCreate(DATABASE_FILE, DatabaseConfig.class, DatabaseConfig::defaults);
        List<String> dbProblems = ConfigValidator.validate(db);
        if (!dbProblems.isEmpty()) {
            throw new ConfigurationException("CV-CONFIG-012",
                    "Invalid database.json:\n  - " + String.join("\n  - ", dbProblems));
        }
        this.databaseConfig = db;

        this.rewardsConfig = loadRewards();
        this.seasonsConfig = loadSeasons();
        this.eventsConfig = loadEvents();

        List<String> crossProblems = ConfigValidator.validateCrossReferences(
                rewardsConfig, seasonsConfig, eventsConfig);
        if (!crossProblems.isEmpty()) {
            throw new ConfigurationException("CV-CONFIG-020",
                    "Invalid cross-config references:\n  - " + String.join("\n  - ", crossProblems));
        }
    }

    private RewardsConfig loadRewards() {
        RewardsConfig rewards = loader.loadOrCreate(REWARDS_FILE, RewardsConfig.class, RewardsConfig::defaults);
        List<String> problems = ConfigValidator.validate(rewards);
        if (!problems.isEmpty()) {
            throw new ConfigurationException("CV-CONFIG-014",
                    "Invalid rewards.json:\n  - " + String.join("\n  - ", problems));
        }
        // Back-fill each definition's id from its map key (ids are not stored in the JSON body).
        rewards.definitions.forEach((id, def) -> def.id = id);
        return rewards;
    }

    private SeasonsConfig loadSeasons() {
        SeasonsConfig seasons = loader.loadOrCreate(SEASONS_FILE, SeasonsConfig.class, SeasonsConfig::defaults);
        List<String> problems = ConfigValidator.validate(seasons);
        if (!problems.isEmpty()) {
            throw new ConfigurationException("CV-CONFIG-016",
                    "Invalid seasons.json:\n  - " + String.join("\n  - ", problems));
        }
        seasons.seasons.forEach((id, def) -> def.id = id);
        return seasons;
    }

    private EventsConfig loadEvents() {
        EventsConfig events = loader.loadOrCreate(EVENTS_FILE, EventsConfig.class, EventsConfig::defaults);
        List<String> problems = ConfigValidator.validate(events);
        if (!problems.isEmpty()) {
            throw new ConfigurationException("CV-CONFIG-018",
                    "Invalid events.json:\n  - " + String.join("\n  - ", problems));
        }
        events.events.forEach((id, def) -> def.id = id);
        return events;
    }

    /**
     * Re-reads the runtime-reloadable config from disk and swaps it in <b>atomically</b>: the new
     * config is fully validated — every file individually <i>and</i> the cross-file references — before
     * any of it goes live. If anything is invalid, nothing is swapped (the previous config stays active
     * in its entirety) and the problems are returned. This prevents a rejected reload from leaving an
     * individually-valid but mutually-incompatible config active.
     *
     * @return list of validation problems; empty on success
     */
    public List<String> reload() {
        List<String> problems = new java.util.ArrayList<>();

        // 1. Load candidates (do not swap yet).
        CoreConfig core = loader.loadOrCreate(CORE_FILE, CoreConfig.class, CoreConfig::defaults);
        RewardsConfig rewards = loader.loadOrCreate(REWARDS_FILE, RewardsConfig.class, RewardsConfig::defaults);
        SeasonsConfig seasons = loader.loadOrCreate(SEASONS_FILE, SeasonsConfig.class, SeasonsConfig::defaults);
        EventsConfig events = loader.loadOrCreate(EVENTS_FILE, EventsConfig.class, EventsConfig::defaults);

        // 2. Validate each file, then (only if all individually valid) their cross-references.
        problems.addAll(ConfigValidator.validate(core));
        problems.addAll(ConfigValidator.validate(rewards));
        problems.addAll(ConfigValidator.validate(seasons));
        problems.addAll(ConfigValidator.validate(events));
        if (problems.isEmpty()) {
            problems.addAll(ConfigValidator.validateCrossReferences(rewards, seasons, events));
        }

        // 3. Swap everything in, or nothing.
        if (!problems.isEmpty()) {
            LOGGER.warn("Reload rejected; keeping previous config in full. {} problem(s).", problems.size());
            return problems;
        }
        rewards.definitions.forEach((id, def) -> def.id = id);
        seasons.seasons.forEach((id, def) -> def.id = id);
        events.events.forEach((id, def) -> def.id = id);
        this.coreConfig = core;
        this.rewardsConfig = rewards;
        this.seasonsConfig = seasons;
        this.eventsConfig = events;
        LOGGER.info("Reloaded configuration (rewards={}, seasons={}, events={})",
                rewards.definitions.size(), seasons.seasons.size(), events.events.size());
        return problems;
    }

    /** The current core config. Never null after {@link #load()} succeeds. */
    public CoreConfig core() {
        CoreConfig current = coreConfig;
        if (current == null) {
            throw new ConfigurationException("CV-CONFIG-011",
                    "Core configuration accessed before load()");
        }
        return current;
    }

    /**
     * The database config. Loaded once at startup and <b>not</b> runtime-reloadable — changing the
     * driver or file requires a full restart, so {@link #reload()} deliberately leaves it untouched.
     */
    public DatabaseConfig database() {
        DatabaseConfig current = databaseConfig;
        if (current == null) {
            throw new ConfigurationException("CV-CONFIG-013",
                    "Database configuration accessed before load()");
        }
        return current;
    }

    /** The reward definitions and currency/template config. Runtime-reloadable. */
    public RewardsConfig rewards() {
        RewardsConfig current = rewardsConfig;
        if (current == null) {
            throw new ConfigurationException("CV-CONFIG-015",
                    "Rewards configuration accessed before load()");
        }
        return current;
    }

    /** The season definitions. Runtime-reloadable. */
    public SeasonsConfig seasons() {
        SeasonsConfig current = seasonsConfig;
        if (current == null) {
            throw new ConfigurationException("CV-CONFIG-017",
                    "Seasons configuration accessed before load()");
        }
        return current;
    }

    /** The event definitions. Runtime-reloadable. */
    public EventsConfig events() {
        EventsConfig current = eventsConfig;
        if (current == null) {
            throw new ConfigurationException("CV-CONFIG-019",
                    "Events configuration accessed before load()");
        }
        return current;
    }

    public ConfigLoader loader() {
        return loader;
    }
}
