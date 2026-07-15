package com.thamescape.cobbleverse.core.event;

import java.util.Set;

/**
 * Lifecycle state of a server event. Transitions are validated by {@link #canTransitionTo}, so an
 * event can only move forward through legal states (or be cancelled from any non-terminal state).
 *
 * <pre>
 *   DRAFT ─▶ SCHEDULED ─▶ OPEN ─▶ ACTIVE ─▶ COMPLETED
 *     └──────────┴─────────┴────────┴──▶ CANCELLED
 * </pre>
 */
public enum EventState {
    DRAFT,
    SCHEDULED,
    OPEN,
    ACTIVE,
    COMPLETED,
    CANCELLED;

    /** States a player may join in. */
    public boolean joinable() {
        return this == OPEN || this == ACTIVE;
    }

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean canTransitionTo(EventState target) {
        return switch (this) {
            case DRAFT -> Set.of(SCHEDULED, OPEN, CANCELLED).contains(target);
            case SCHEDULED -> Set.of(OPEN, CANCELLED).contains(target);
            case OPEN -> Set.of(ACTIVE, CANCELLED).contains(target);
            case ACTIVE -> Set.of(COMPLETED, CANCELLED).contains(target);
            case COMPLETED, CANCELLED -> false;
        };
    }
}
