package com.thamescape.cobbleverse.core.config;

/**
 * Database configuration, persisted as {@code config/cobbleverse-server-core/database.json}.
 *
 * <p>0.2.0 supports SQLite only. The {@code type} field exists so PostgreSQL / MariaDB can be added
 * later without changing the file shape.
 */
public class DatabaseConfig {

    public int configVersion = CURRENT_VERSION;

    /** Backend type. Only {@code sqlite} is supported in 0.2.0. */
    public String type = "sqlite";

    /** SQLite database file, relative to the core config directory. */
    public String fileName = "data/core.db";

    /** How often dirty player profiles are written back to disk. */
    public int flushIntervalSeconds = 300;

    /** How often online players' playtime is accrued into their profiles. */
    public int playtimeAccrualSeconds = 60;

    public static final int CURRENT_VERSION = 1;

    public static DatabaseConfig defaults() {
        return new DatabaseConfig();
    }
}
