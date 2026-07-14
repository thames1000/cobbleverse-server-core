package com.thamescape.cobbleverse.core.player;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of loaded profiles (typically the online players) plus a dirty set marking
 * profiles with unsaved changes. Reads on the hot path never touch the database.
 *
 * <p>Mutation happens on the server thread; the map is concurrent so status reads from elsewhere are
 * safe. {@link #snapshotDirtyAndClear()} produces DB-worker-safe copies.
 */
public final class PlayerProfileCache {

    private final ConcurrentHashMap<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    @Nullable
    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    public boolean contains(UUID uuid) {
        return profiles.containsKey(uuid);
    }

    public void put(PlayerProfile profile) {
        profiles.put(profile.uuid(), profile);
    }

    public void remove(UUID uuid) {
        profiles.remove(uuid);
        dirty.remove(uuid);
    }

    public void markDirty(UUID uuid) {
        if (profiles.containsKey(uuid)) {
            dirty.add(uuid);
        }
    }

    public int size() {
        return profiles.size();
    }

    public int dirtyCount() {
        return dirty.size();
    }

    public Collection<PlayerProfile> all() {
        return new ArrayList<>(profiles.values());
    }

    /** Returns snapshots of all currently-dirty profiles and clears the dirty set. */
    public List<PlayerProfile> snapshotDirtyAndClear() {
        List<PlayerProfile> out = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(dirty)) {
            PlayerProfile profile = profiles.get(uuid);
            if (profile != null) {
                out.add(profile.snapshot());
            }
            dirty.remove(uuid);
        }
        return out;
    }
}
