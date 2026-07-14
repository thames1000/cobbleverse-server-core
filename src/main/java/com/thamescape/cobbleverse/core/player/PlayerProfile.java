package com.thamescape.cobbleverse.core.player;

import java.util.UUID;

/**
 * Server-owned data for one player. Deliberately thin in 0.2.0 — identity and playtime only. Later
 * systems (seasons, currencies, titles) add their own fields via schema migrations rather than
 * pre-creating empty columns now.
 *
 * <p>Mutable, but only ever mutated on the server thread (lifecycle + scheduler tasks). Database
 * writes operate on a {@link #snapshot()} taken on the server thread, so the worker thread never
 * observes a half-updated profile.
 */
public final class PlayerProfile {

    private final UUID uuid;
    private String lastKnownName;
    private final long firstJoinedAt;
    private long lastJoinedAt;
    private long playtimeSeconds;

    public PlayerProfile(UUID uuid, String lastKnownName, long firstJoinedAt,
                         long lastJoinedAt, long playtimeSeconds) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.firstJoinedAt = firstJoinedAt;
        this.lastJoinedAt = lastJoinedAt;
        this.playtimeSeconds = playtimeSeconds;
    }

    /** Creates a brand-new profile for a first-time join. */
    public static PlayerProfile createNew(UUID uuid, String name, long now) {
        return new PlayerProfile(uuid, name, now, now, 0L);
    }

    public UUID uuid() {
        return uuid;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public long firstJoinedAt() {
        return firstJoinedAt;
    }

    public long lastJoinedAt() {
        return lastJoinedAt;
    }

    public void setLastJoinedAt(long lastJoinedAt) {
        this.lastJoinedAt = lastJoinedAt;
    }

    public long playtimeSeconds() {
        return playtimeSeconds;
    }

    public void addPlaytimeSeconds(long seconds) {
        if (seconds > 0) {
            this.playtimeSeconds += seconds;
        }
    }

    /** An immutable-for-practical-purposes copy, safe to hand to the database worker thread. */
    public PlayerProfile snapshot() {
        return new PlayerProfile(uuid, lastKnownName, firstJoinedAt, lastJoinedAt, playtimeSeconds);
    }
}
