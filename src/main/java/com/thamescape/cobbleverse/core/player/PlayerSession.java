package com.thamescape.cobbleverse.core.player;

import java.util.UUID;

/**
 * Tracks an online player's current session for playtime accrual. {@code lastAccrualMillis} marks the
 * point up to which playtime has already been credited; advancing it by whole seconds preserves the
 * sub-second remainder so no time is lost across accruals.
 */
public final class PlayerSession {

    private final UUID uuid;
    private final long joinedAtMillis;
    private long lastAccrualMillis;

    public PlayerSession(UUID uuid, long now) {
        this.uuid = uuid;
        this.joinedAtMillis = now;
        this.lastAccrualMillis = now;
    }

    public UUID uuid() {
        return uuid;
    }

    public long joinedAtMillis() {
        return joinedAtMillis;
    }

    public long lastAccrualMillis() {
        return lastAccrualMillis;
    }

    public void advance(long millis) {
        this.lastAccrualMillis += millis;
    }
}
