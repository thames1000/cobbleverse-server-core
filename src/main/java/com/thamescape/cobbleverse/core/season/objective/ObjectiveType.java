package com.thamescape.cobbleverse.core.season.objective;

import java.util.Locale;

/**
 * Known objective types. 0.4.0 ships only {@link #MANUAL} (progress driven by admins or other
 * modules); event-driven types (catches, battles, raids, ...) are added with Cobblemon tracking and
 * register their own handlers, so this never becomes a giant switch.
 */
public enum ObjectiveType {
    MANUAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
