package com.thamescape.cobbleverse.core.event;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A player's participation in an event: when they joined and their current score. */
public record EventParticipant(UUID uuid, @Nullable String name, long joinedAt, int score) {

    /** Display name if known, else a short UUID. */
    public String label() {
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
