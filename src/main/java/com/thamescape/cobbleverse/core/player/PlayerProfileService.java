package com.thamescape.cobbleverse.core.player;

import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.repository.PlayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns player profiles: loads them on join, accrues playtime, flushes changes, and serves reads.
 *
 * <p>Threading model: all profile mutation happens on the server thread (join/leave lifecycle and
 * scheduler tasks, which tick on the server thread). Database reads on join and for commands are
 * short indexed point queries run synchronously; periodic and leave writes are asynchronous so the
 * server thread is never blocked on a write.
 */
public final class PlayerProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/PLAYER");

    private final DatabaseManager db;
    private final PlayerProfileRepository repository;
    private final PlayerProfileCache cache = new PlayerProfileCache();
    private final ConcurrentHashMap<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public PlayerProfileService(DatabaseManager db, PlayerProfileRepository repository) {
        this.db = db;
        this.repository = repository;
    }

    /** Called on player join: loads or creates the profile and starts a session. */
    public void onJoin(UUID uuid, String name, long now) {
        PlayerProfile profile = db.callSync(conn -> repository.find(conn, uuid).orElse(null));
        if (profile == null) {
            profile = PlayerProfile.createNew(uuid, name, now);
            LOGGER.debug("Created profile for {} ({})", name, uuid);
        } else {
            profile.setLastKnownName(name);
            profile.setLastJoinedAt(now);
        }
        cache.put(profile);
        cache.markDirty(uuid);
        sessions.put(uuid, new PlayerSession(uuid, now));
    }

    /** Called on player leave: final accrual, then an async write-back. */
    public void onLeave(UUID uuid, long now) {
        accrue(uuid, now);
        PlayerProfile cached = cache.get(uuid);
        PlayerProfile snapshot = cached != null ? cached.snapshot() : null;
        sessions.remove(uuid);
        cache.remove(uuid);
        if (snapshot != null) {
            db.runAsync(conn -> repository.upsert(conn, snapshot))
                    .exceptionally(t -> {
                        LOGGER.warn("Failed to persist profile for {} on leave: {}", uuid, t.getMessage());
                        return null;
                    });
        }
    }

    /** Credits elapsed whole seconds since the last accrual to every online player's profile. */
    public void accrueAll(long now) {
        for (UUID uuid : sessions.keySet()) {
            accrue(uuid, now);
        }
    }

    private void accrue(UUID uuid, long now) {
        PlayerSession session = sessions.get(uuid);
        PlayerProfile profile = cache.get(uuid);
        if (session == null || profile == null) {
            return;
        }
        long elapsedMillis = now - session.lastAccrualMillis();
        long seconds = elapsedMillis / 1000L;
        if (seconds > 0) {
            profile.addPlaytimeSeconds(seconds);
            session.advance(seconds * 1000L);
            cache.markDirty(uuid);
        }
    }

    /** Writes all dirty profiles asynchronously in a single transaction. */
    public void flushDirty() {
        List<PlayerProfile> dirty = cache.snapshotDirtyAndClear();
        if (dirty.isEmpty()) {
            return;
        }
        db.runInTransactionAsync(conn -> {
            for (PlayerProfile profile : dirty) {
                repository.upsert(conn, profile);
            }
        }).exceptionally(t -> {
            LOGGER.warn("Profile flush failed ({} profiles): {}", dirty.size(), t.getMessage());
            return null;
        });
    }

    /** Synchronous flush of every cached profile — used on shutdown before the DB closes. */
    public void flushAllBlocking(long now) {
        accrueAll(now);
        List<PlayerProfile> all = cache.all().stream().map(PlayerProfile::snapshot).toList();
        if (all.isEmpty()) {
            return;
        }
        db.runSync(conn -> {
            for (PlayerProfile profile : all) {
                repository.upsert(conn, profile);
            }
        });
        LOGGER.info("Flushed {} profile(s) on shutdown", all.size());
    }

    /** Outcome of {@link #createIfAbsent}. */
    public enum ProfileCreation {
        CREATED,
        ALREADY_EXISTS
    }

    /**
     * Ensures a profile exists for {@code uuid} without treating the player as online (no session is
     * started). Used to pre-seed a profile for a player who has not joined yet. Never overwrites an
     * existing profile.
     *
     * <p>May be called off the server thread (e.g. from an async name lookup); all database access
     * goes through the worker thread, and the cache is concurrent.
     */
    public ProfileCreation createIfAbsent(UUID uuid, String name, long now) {
        if (cache.contains(uuid)) {
            return ProfileCreation.ALREADY_EXISTS;
        }
        boolean exists = db.callSync(conn -> repository.find(conn, uuid).isPresent());
        if (exists) {
            return ProfileCreation.ALREADY_EXISTS;
        }
        PlayerProfile profile = PlayerProfile.createNew(uuid, name, now);
        db.runSync(conn -> repository.upsert(conn, profile));
        LOGGER.info("Pre-created profile for {} ({})", name, uuid);
        return ProfileCreation.CREATED;
    }

    /** Looks up a profile by UUID: cache first, then a synchronous DB read for offline players. */
    public Optional<PlayerProfile> find(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        return db.callSync(conn -> repository.find(conn, uuid));
    }

    /** Looks up an offline player's profile by last known name (case-insensitive). */
    public Optional<PlayerProfile> findByName(String name) {
        return db.callSync(conn -> repository.findByName(conn, name));
    }

    public int onlineCount() {
        return sessions.size();
    }

    public int cachedCount() {
        return cache.size();
    }

    public int dirtyCount() {
        return cache.dirtyCount();
    }

    public long storedCount() {
        return db.callSync(repository::count);
    }
}
