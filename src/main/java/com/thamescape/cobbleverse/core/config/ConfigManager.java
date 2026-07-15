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
     * Re-reads config from disk and swaps it in only if valid. On failure the previous config stays
     * active and the problems are returned so the caller can report them.
     *
     * @return list of validation problems; empty on success
     */
    public List<String> reload() {
        List<String> problems = new java.util.ArrayList<>();

        CoreConfig loaded = loader.loadOrCreate(CORE_FILE, CoreConfig.class, CoreConfig::defaults);
        List<String> coreProblems = ConfigValidator.validate(loaded);
        if (coreProblems.isEmpty()) {
            this.coreConfig = loaded;
            LOGGER.info("Reloaded core configuration");
        } else {
            LOGGER.warn("core.json reload rejected; keeping previous. {} problem(s).", coreProblems.size());
            problems.addAll(coreProblems);
        }

        RewardsConfig rewards = loader.loadOrCreate(REWARDS_FILE, RewardsConfig.class, RewardsConfig::defaults);
        List<String> rewardProblems = ConfigValidator.validate(rewards);
        if (rewardProblems.isEmpty()) {
            rewards.definitions.forEach((id, def) -> def.id = id);
            this.rewardsConfig = rewards;
            LOGGER.info("Reloaded reward definitions ({})", rewards.definitions.size());
        } else {
            LOGGER.warn("rewards.json reload rejected; keeping previous. {} problem(s).", rewardProblems.size());
            problems.addAll(rewardProblems);
        }

        SeasonsConfig seasons = loader.loadOrCreate(SEASONS_FILE, SeasonsConfig.class, SeasonsConfig::defaults);
        List<String> seasonProblems = ConfigValidator.validate(seasons);
        if (seasonProblems.isEmpty()) {
            seasons.seasons.forEach((id, def) -> def.id = id);
            this.seasonsConfig = seasons;
            LOGGER.info("Reloaded season definitions ({})", seasons.seasons.size());
        } else {
            LOGGER.warn("seasons.json reload rejected; keeping previous. {} problem(s).", seasonProblems.size());
            problems.addAll(seasonProblems);
        }

        EventsConfig events = loader.loadOrCreate(EVENTS_FILE, EventsConfig.class, EventsConfig::defaults);
        List<String> eventProblems = ConfigValidator.validate(events);
        if (eventProblems.isEmpty()) {
            events.events.forEach((id, def) -> def.id = id);
            this.eventsConfig = events;
            LOGGER.info("Reloaded event definitions ({})", events.events.size());
        } else {
            LOGGER.warn("events.json reload rejected; keeping previous. {} problem(s).", eventProblems.size());
            problems.addAll(eventProblems);
        }

        // Cross-config integrity across the now-live configs.
        problems.addAll(ConfigValidator.validateCrossReferences(rewardsConfig, seasonsConfig, eventsConfig));

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
