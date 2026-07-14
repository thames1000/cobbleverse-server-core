package com.thamescape.cobbleverse.core.permission;

/**
 * Canonical permission node strings for the core. Feature modules should reference these constants
 * rather than typing raw strings so nodes stay consistent.
 *
 * <p>All nodes live under the {@code cobbleverse.} namespace so LuckPerms can manage them with a
 * single wildcard ({@code cobbleverse.*}).
 */
public final class CorePermissions {

    private CorePermissions() {
    }

    // Command access
    public static final String COMMAND_CVCORE = "cobbleverse.command.cvcore";
    public static final String COMMAND_PROFILE = "cobbleverse.command.profile";

    // Player-facing
    public static final String PROFILE_VIEW = "cobbleverse.profile.view";
    public static final String PROFILE_VIEW_OTHER = "cobbleverse.profile.view.other";

    // Administrative actions
    public static final String ADMIN_RELOAD = "cobbleverse.admin.reload";
    public static final String ADMIN_DEBUG = "cobbleverse.admin.debug";
    public static final String ADMIN_INTEGRATIONS = "cobbleverse.admin.integrations";
    public static final String ADMIN_DATABASE = "cobbleverse.admin.database";
    public static final String ADMIN_PLAYER = "cobbleverse.admin.player";
}
