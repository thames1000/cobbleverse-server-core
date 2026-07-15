package com.thamescape.cobbleverse.core.game.player;

import com.thamescape.cobbleverse.core.game.GameEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A player left the server. */
public record PlayerLeftGameEvent(UUID playerUuid, Instant timestamp, String name) implements GameEvent {

    public static final String TYPE = "player_left";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String source() {
        return "minecraft";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("name", name);
    }
}
