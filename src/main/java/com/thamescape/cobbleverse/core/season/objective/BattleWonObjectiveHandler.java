package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.battle.BattleWonGameEvent;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;

/**
 * Progresses when a battle is won. Optionally filtered to a {@code battleKind}
 * ({@code pvp}/{@code pvn}/{@code pvw}); blank matches any non-wild battle. Wild captures (which
 * Cobblemon reports as a battle victory) are excluded so they don't double-count with capture
 * objectives.
 */
public final class BattleWonObjectiveHandler implements ObjectiveHandler {

    @Override
    public String type() {
        return ObjectiveType.BATTLE_WON.id();
    }

    @Override
    public int progressFor(GameEvent event, ObjectiveDefinition objective) {
        if (!(event instanceof BattleWonGameEvent battle) || battle.wildCapture()) {
            return 0;
        }
        if (objective.battleKind != null && !objective.battleKind.isBlank()
                && !objective.battleKind.equalsIgnoreCase(battle.battleKind())) {
            return 0;
        }
        return 1;
    }
}
