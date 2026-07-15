package com.thamescape.cobbleverse.core.api;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.reward.RewardResult;

import java.util.UUID;

/**
 * The stable, read-and-act facade for mods built on Cobbleverse Server Core. Obtain it with
 * {@link #get()} once the server has started (see {@link #isReady()}); calls before startup completes
 * throw. This is the supported surface — prefer it over reaching into the core's internal services,
 * which may change between versions.
 *
 * <p>To <b>extend</b> the core (add objective types, listeners, currencies, health checks), implement
 * {@link CobbleverseExtension} instead — that runs at the right point in startup.
 *
 * <p><b>Experimental (0.8.0):</b> this surface may change until it is frozen in 1.0.
 */
public interface CobbleverseApi {

    /** The live API instance. Throws if the core has not finished starting yet ({@link #isReady()}). */
    static CobbleverseApi get() {
        return CobbleverseApiHolder.require();
    }

    /** Whether {@link #get()} will succeed (the core has published its services). */
    static boolean isReady() {
        return CobbleverseApiHolder.isReady();
    }

    /** The season id the server is configured to feature (may not currently be ACTIVE). */
    String activeSeasonId();

    /** Whether the configured season exists and is currently ACTIVE. */
    boolean isSeasonActive();

    /** A player's points in the given season (0 if none). */
    int seasonPoints(UUID player, String seasonId);

    /** A player's value for a statistic key (0 if never recorded). */
    long statistic(UUID player, String stat);

    /**
     * Grants a configured reward to a player through the central reward service (claim dedup + offline
     * queue inherited). {@code source} is recorded for auditing.
     */
    RewardResult grantReward(UUID player, String rewardId, String source);

    /** Publishes a game event onto the bus, reaching every listener (built-in and extension). */
    void publishGameEvent(GameEvent event);
}
