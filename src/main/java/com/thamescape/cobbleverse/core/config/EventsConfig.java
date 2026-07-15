package com.thamescape.cobbleverse.core.config;

import com.thamescape.cobbleverse.core.event.EventDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event configuration, persisted as {@code config/cobbleverse-server-core/events.json}. Definitions
 * are static; each event's live state and participants live in the database.
 */
public class EventsConfig {

    public int configVersion = CURRENT_VERSION;

    public Map<String, EventDefinition> events = new LinkedHashMap<>();

    public static final int CURRENT_VERSION = 1;

    public static EventsConfig defaults() {
        EventsConfig config = new EventsConfig();

        EventDefinition sample = new EventDefinition();
        sample.displayName = "Sample Catching Event";
        sample.description = "A sample event. Configure your own in events.json.";
        sample.type = "catching";
        sample.rewards = List.of("sample_tier_1");

        config.events.put("sample_event", sample);
        return config;
    }
}
