package com.thamescape.cobbleverse.core.statistics;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.GameEventListener;
import com.thamescape.cobbleverse.core.game.battle.BattleWonGameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.game.player.PlayerJoinedGameEvent;

import java.util.UUID;

/**
 * Updates player statistics from game events — a bus consumer that knows nothing about seasons or
 * rewards. Adding a tracked stat means editing only this class (and, if new, a stat key).
 */
public final class StatisticsGameEventListener implements GameEventListener {

    private final StatisticsService statistics;

    public StatisticsGameEventListener(StatisticsService statistics) {
        this.statistics = statistics;
    }

    @Override
    public String name() {
        return "Statistics";
    }

    @Override
    public void onGameEvent(GameEvent event) {
        UUID uuid = event.playerUuid();
        if (uuid == null) {
            return;
        }
        if (event instanceof PokemonCapturedGameEvent capture) {
            statistics.increment(uuid, StatisticsService.CAPTURES, 1);
            if (capture.shiny()) {
                statistics.increment(uuid, StatisticsService.SHINIES, 1);
            }
        } else if (event instanceof BattleWonGameEvent battle && !battle.wildCapture()) {
            statistics.increment(uuid, StatisticsService.BATTLES_WON, 1);
        } else if (event instanceof PlayerJoinedGameEvent) {
            statistics.increment(uuid, StatisticsService.SESSIONS, 1);
        }
    }
}
