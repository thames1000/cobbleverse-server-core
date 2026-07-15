package com.thamescape.cobbleverse.core.game;

/**
 * A subscriber to the {@link GameEventBus}. Implementations filter by {@link GameEvent#type()} (or
 * {@code instanceof}) for the events they care about. A listener must not throw — the bus isolates
 * exceptions, but a well-behaved listener handles its own errors.
 */
public interface GameEventListener {

    void onGameEvent(GameEvent event);

    /** Short name for logs and diagnostics. */
    default String name() {
        return getClass().getSimpleName();
    }
}
