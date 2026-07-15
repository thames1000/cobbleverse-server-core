package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;

/**
 * Interprets game events for one objective {@code type}. An event-driven handler (e.g. "catch a
 * water type") inspects a {@link GameEvent} and reports how much progress it contributes toward an
 * objective of its type; the season system applies that progress. Handlers register in an
 * {@link ObjectiveRegistry}, so adding a type never touches a central switch.
 *
 * <p>The {@code manual} type is admin/other-module-driven only and never auto-progresses.
 */
public interface ObjectiveHandler {

    /** The type id this handler tracks, e.g. {@code capture_species}. */
    String type();

    /**
     * Progress this event contributes toward an objective of this handler's type. Returns 0 if the
     * event does not match (wrong event type, wrong species, etc.).
     */
    default int progressFor(GameEvent event, ObjectiveDefinition objective) {
        return 0;
    }

    /** Whether this type is driven externally (admin / another module) rather than by game events. */
    default boolean manual() {
        return false;
    }
}
