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

    // Type-specific matchers (read by the relevant ObjectiveHandler; null/blank means "any").
    /** {@code capture_species}: the species to match (case-insensitive). */
    public String species;
    /** {@code battle_won}: the battle kind to match ({@code pvp}/{@code pvn}/{@code pvw}); blank = any. */
    public String battleKind;

    public String displayNameOrId() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }
}
