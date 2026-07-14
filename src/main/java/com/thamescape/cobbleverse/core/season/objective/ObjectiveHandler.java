package com.thamescape.cobbleverse.core.season.objective;

/**
 * Tracks progress for one objective {@code type}. In 0.4.0 there is only the manual handler (no
 * automatic tracking). Later, event-driven handlers (e.g. "catch a water type") subscribe to game
 * events and call {@code SeasonService.addObjectiveProgress}; they register here so the season system
 * stays open for extension without a central switch.
 */
public interface ObjectiveHandler {

    /** The type id this handler tracks, e.g. {@code manual}. */
    String type();

    /** Whether progress for this type is driven externally (admin / another module) rather than auto. */
    default boolean manual() {
        return true;
    }
}
