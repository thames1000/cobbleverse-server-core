package com.thamescape.cobbleverse.core.game.battle;

import com.thamescape.cobbleverse.core.game.GameEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A player won a battle. Carries enough context for consumers to distinguish the kinds of win likely
 * to matter for objectives (a trainer/NPC battle vs a wild encounter, singles vs doubles, PvP, ...).
 *
 * @param battleKind {@code pvp} (vs another player), {@code pvn} (vs an NPC/trainer), {@code pvw} (vs
 *                   wild), or {@code other}
 * @param format     the battle format's type name, e.g. {@code singles} / {@code doubles}
 * @param wildCapture whether the victory was a wild capture
 */
public record BattleWonGameEvent(
        UUID playerUuid, Instant timestamp, String battleKind, String format, boolean wildCapture)
        implements GameEvent {

    public static final String TYPE = "battle_won";

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
        return Map.of("battleKind", battleKind, "format", format, "wildCapture", wildCapture);
    }
}
