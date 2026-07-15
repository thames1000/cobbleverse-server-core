package com.thamescape.cobbleverse.core.game.player;

import com.thamescape.cobbleverse.core.game.GameEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A player joined the server. */
public record PlayerJoinedGameEvent(UUID playerUuid, Instant timestamp, String name) implements GameEvent {

    public static final String TYPE = "player_joined";

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
