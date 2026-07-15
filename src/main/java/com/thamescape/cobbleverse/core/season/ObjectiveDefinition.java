package com.thamescape.cobbleverse.core.season;

/**
 * A season objective, loaded from {@code seasons.json}. In 0.4.0 the only {@code type} is
 * {@code manual} (progress driven by admins or other modules); event-driven types (catches, battles,
 * ...) arrive with Cobblemon tracking in a later version.
 */
public class ObjectiveDefinition {

    public String id;
    public String displayName = "";
    public String type = "manual";

    /** Progress needed to complete the objective. */
    public int required = 1;

    /** Season points awarded on completion. */
    public int points = 0;

    public String displayNameOrId() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }
}
