package com.thamescape.cobbleverse.core.event;

import java.util.ArrayList;
import java.util.List;

/**
 * A server event definition, loaded from {@code events.json}. Its live {@link EventState} and its
 * participants are stored in the database; this holds the static shape.
 */
public class EventDefinition {

    public transient String id;
    public String displayName = "";
    public String description = "";

    /** Informational category (safari, tournament, catching, raid, community_goal, ...). */
    public String type = "generic";

    /** Optional ISO-8601 offset date-times for future auto-scheduling (not enforced in 0.5.0). */
    public String scheduledStart;
    public String scheduledEnd;

    /** Reward definition ids granted to each participant when the event completes. */
    public List<String> rewards = new ArrayList<>();

    public String displayNameOrId() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }
}
