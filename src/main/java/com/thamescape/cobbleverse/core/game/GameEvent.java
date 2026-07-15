package com.thamescape.cobbleverse.core.game;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A game-world occurrence, published to the {@link GameEventBus} and consumed by subscribers. This is
 * the ingestion layer's core contract: producers (a Cobblemon adapter, player lifecycle, ...) publish
 * these, and consumers (seasons, events, statistics, ... — added in later versions) react. Producers
 * never import consumer services, and vice versa.
 *
 * <p>Implementations are immutable records. Every event carries a stable {@link #type()} string, an
 * optional player, a timestamp, a source, and a free-form {@link #metadata()} map for details a
 * generic consumer can read without knowing the concrete type.
 */
public interface GameEvent {

    /** The player this event concerns, or {@code null} for player-agnostic events. */
    @Nullable
    UUID playerUuid();

    /** When the event occurred. */
    Instant timestamp();

    /** Stable type id, e.g. {@code pokemon_captured} — used by consumers to match events. */
    String type();

    /** The producing subsystem, e.g. {@code cobblemon} or {@code minecraft}. */
    default String source() {
        return "core";
    }

    /** Type-specific details, readable generically (e.g. {@code species}, {@code shiny}). */
    default Map<String, Object> metadata() {
        return Map.of();
    }
}
