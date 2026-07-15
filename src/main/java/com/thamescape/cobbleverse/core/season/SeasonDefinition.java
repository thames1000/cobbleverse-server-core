package com.thamescape.cobbleverse.core.season;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A season definition, loaded from {@code seasons.json}. {@link #id} is filled from the map key.
 * The active/upcoming/ended {@link SeasonState} is derived from {@link #startsAt}/{@link #endsAt} at
 * runtime, not stored here.
 */
public class SeasonDefinition {

    public transient String id;
    public String displayName = "";

    /** ISO-8601 offset date-times, e.g. {@code 2026-07-01T00:00:00-04:00}. */
    public String startsAt;
    public String endsAt;

    public boolean enabled = true;

    public List<ObjectiveDefinition> objectives = new ArrayList<>();
    public List<Milestone> milestones = new ArrayList<>();

    public String displayNameOrId() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    public Optional<ObjectiveDefinition> objective(String objectiveId) {
        return objectives.stream().filter(o -> objectiveId.equals(o.id)).findFirst();
    }
}
