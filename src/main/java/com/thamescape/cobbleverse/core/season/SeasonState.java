package com.thamescape.cobbleverse.core.season;

/** Lifecycle state of a season, derived from its configured window and enabled flag. */
public enum SeasonState {
    /** Not enabled in config. */
    DISABLED,
    /** Enabled but its start time is in the future. */
    UPCOMING,
    /** Enabled and within its window. */
    ACTIVE,
    /** Enabled but its end time has passed. */
    ENDED
}
