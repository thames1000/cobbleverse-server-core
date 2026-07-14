package com.thamescape.cobbleverse.core.config;

/**
 * Top-level core configuration, persisted as {@code config/cobbleverse-server-core/core.json}.
 *
 * <p>Fields are mutable with sane defaults so a config missing a key keeps the default rather than
 * failing. Structural problems are caught by {@link ConfigValidator}, not by deserialization.
 */
public class CoreConfig {

    /** Schema version for this file. Bumped when the shape changes so migrations can run. */
    public int configVersion = CURRENT_VERSION;

    public boolean debug = false;
    public String serverId = "cobbleverse";
    public String environment = "production";
    public String defaultLocale = "en_us";
    public String timezone = "America/New_York";
    public String activeSeason = "";
    public boolean enableAuditLog = true;
    public boolean enableMetrics = true;

    /** The config version this build knows how to read/write. */
    public static final int CURRENT_VERSION = 1;

    /** Returns a fresh instance populated with defaults. */
    public static CoreConfig defaults() {
        return new CoreConfig();
    }
}
