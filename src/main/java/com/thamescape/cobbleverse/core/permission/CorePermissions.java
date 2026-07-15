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
    public static final String COMMAND_REWARDS = "cobbleverse.command.rewards";
    public static final String COMMAND_SEASON = "cobbleverse.command.season";
    public static final String COMMAND_EVENTS = "cobbleverse.command.events";

    // Player-facing
    public static final String PROFILE_VIEW = "cobbleverse.profile.view";
    public static final String PROFILE_VIEW_OTHER = "cobbleverse.profile.view.other";
    public static final String REWARD_CLAIM = "cobbleverse.reward.claim";
    public static final String REWARD_PREVIEW = "cobbleverse.reward.preview";
    public static final String SEASON_VIEW = "cobbleverse.season.view";
    public static final String SEASON_PROGRESS = "cobbleverse.season.progress";
    public static final String EVENT_JOIN = "cobbleverse.event.join";
    public static final String EVENT_LEAVE = "cobbleverse.event.leave";

    // Administrative actions
    public static final String ADMIN_RELOAD = "cobbleverse.admin.reload";
    public static final String ADMIN_DEBUG = "cobbleverse.admin.debug";
    public static final String ADMIN_INTEGRATIONS = "cobbleverse.admin.integrations";
    public static final String ADMIN_DATABASE = "cobbleverse.admin.database";
    public static final String ADMIN_PLAYER = "cobbleverse.admin.player";
    public static final String ADMIN_REWARDS = "cobbleverse.admin.rewards";
    public static final String ADMIN_SEASON = "cobbleverse.admin.season";
    public static final String ADMIN_EVENTS = "cobbleverse.admin.events";
}
