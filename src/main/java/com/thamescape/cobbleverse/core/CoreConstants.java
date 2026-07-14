package com.thamescape.cobbleverse.core;

/**
 * Compile-time constants shared across the core. Anything that can change at runtime belongs in
 * configuration, not here.
 */
public final class CoreConstants {

    private CoreConstants() {
    }

    /** Fabric mod id. Must match {@code fabric.mod.json}. */
    public static final String MOD_ID = "cobbleverse_server_core";

    /** Human-readable name used in log lines and command output. */
    public static final String MOD_NAME = "Cobbleverse Server Core";

    /** Root permission namespace. All permission nodes live under this prefix. */
    public static final String PERMISSION_NAMESPACE = "cobbleverse";

    /** Configuration directory name under the server {@code config/} folder. */
    public static final String CONFIG_DIR_NAME = "cobbleverse-server-core";

    /**
     * Operator level required as a fallback when no permission provider (e.g. LuckPerms) answers a
     * check. Level 4 preserves emergency access for server operators.
     */
    public static final int ADMIN_FALLBACK_LEVEL = 4;
}
