package com.thamescape.cobbleverse.core.statistics;

import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.repository.StatisticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Per-player statistics, updated from game events and read by commands. Increments are asynchronous
 * (fire-and-forget on the database worker) since they're frequent and don't need to block the caller;
 * reads are synchronous for commands.
 */
public final class StatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/STATS");

    // Well-known stat keys.
    public static final String CAPTURES = "captures";
    public static final String SHINIES = "shinies";
    public static final String BATTLES_WON = "battles_won";
    public static final String SESSIONS = "sessions";

    private final DatabaseManager db;
    private final StatisticsRepository repository;

    public StatisticsService(DatabaseManager db, StatisticsRepository repository) {
        this.db = db;
        this.repository = repository;
    }

    /** Adds {@code delta} to a player's stat asynchronously. */
    public void increment(UUID uuid, String stat, long delta) {
        db.runAsync(conn -> repository.increment(conn, uuid, stat, delta))
                .exceptionally(t -> {
                    LOGGER.warn("Failed to increment stat '{}' for {}", stat, uuid, t);
                    return null;
                });
    }

    public long value(UUID uuid, String stat) {
        return db.callSync(conn -> repository.value(conn, uuid, stat));
    }

    public Map<String, Long> all(UUID uuid) {
        return db.callSync(conn -> repository.all(conn, uuid));
    }
}
