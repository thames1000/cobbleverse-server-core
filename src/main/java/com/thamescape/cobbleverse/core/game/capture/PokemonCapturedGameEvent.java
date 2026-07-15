package com.thamescape.cobbleverse.core.game.capture;

import com.thamescape.cobbleverse.core.game.GameEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A player captured a Pokémon. Published by the Cobblemon adapter (or synthetically via
 * {@code /cvcore debug publish capture} for testing).
 */
public record PokemonCapturedGameEvent(
        UUID playerUuid, Instant timestamp, String species, boolean shiny) implements GameEvent {

    public static final String TYPE = "pokemon_captured";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String source() {
        return "cobblemon";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("species", species, "shiny", shiny);
    }
}
