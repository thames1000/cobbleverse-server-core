package com.thamescape.cobbleverse.core.game.battle;

import com.thamescape.cobbleverse.core.game.GameEvent;

import java.time.Instant;
import java.util.UUID;

/** A player won a battle. Published by the Cobblemon adapter. */
public record BattleWonGameEvent(UUID playerUuid, Instant timestamp) implements GameEvent {

    public static final String TYPE = "battle_won";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String source() {
        return "cobblemon";
    }
}
