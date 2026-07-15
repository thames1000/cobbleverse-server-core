package com.thamescape.cobbleverse.core.season.objective;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Maps objective type ids to their handlers. Future modules register event-driven types here. */
public final class ObjectiveRegistry {

    private final Map<String, ObjectiveHandler> handlers = new ConcurrentHashMap<>();

    public void register(ObjectiveHandler handler) {
        handlers.put(handler.type(), handler);
    }

    public Optional<ObjectiveHandler> handler(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    public boolean isKnown(String type) {
        return handlers.containsKey(type);
    }

    public Set<String> types() {
        return handlers.keySet();
    }
}
