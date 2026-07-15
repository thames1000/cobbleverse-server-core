package com.thamescape.cobbleverse.core.web.api;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The read-only data seam the {@link ApiRouter} serves. Each method returns a Gson {@link JsonObject}
 * ready to write, or {@code null} to signal "not found" (the router turns that into a 404). Keeping the
 * router behind this narrow interface lets it be unit-tested with a fake, without a live database.
 */
public interface ApiData {

    /** Health/status summary. Always present (served without auth). */
    JsonObject health();

    /** A season's state; {@code seasonId} null/blank means the configured season. Null if unknown. */
    @Nullable
    JsonObject season(@Nullable String seasonId);

    /** A season points leaderboard; {@code seasonId} null/blank means the configured season. Null if unknown. */
    @Nullable
    JsonObject leaderboard(@Nullable String seasonId, int limit);

    /** An event's state and standings. Null if the event id is unknown. */
    @Nullable
    JsonObject event(String eventId);

    /** A player's profile, season progress and stats. Null if the player has no profile. */
    @Nullable
    JsonObject player(UUID uuid);

    /** A player's statistics (zeros if never recorded). Null if the player has no profile. */
    @Nullable
    JsonObject stats(UUID uuid);
}
