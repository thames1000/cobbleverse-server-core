package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.GameEventListener;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;
import com.thamescape.cobbleverse.core.season.SeasonDefinition;
import com.thamescape.cobbleverse.core.season.SeasonService;

import java.util.UUID;

/**
 * The bridge from the {@link com.thamescape.cobbleverse.core.game.GameEventBus} to season objectives:
 * for each game event, it advances any active-season objective whose handler matches. This is the
 * <b>only</b> class that couples game events to seasons — capture code never imports the season
 * system, and the season system never imports game events.
 */
public final class SeasonObjectiveEventListener implements GameEventListener {

    private final SeasonService seasons;

    public SeasonObjectiveEventListener(SeasonService seasons) {
        this.seasons = seasons;
    }

    @Override
    public String name() {
        return "SeasonObjectives";
    }

    @Override
    public void onGameEvent(GameEvent event) {
        UUID uuid = event.playerUuid();
        if (uuid == null || !seasons.isConfiguredSeasonActive()) {
            return;
        }
        SeasonDefinition season = seasons.configuredSeason().orElse(null);
        if (season == null) {
            return;
        }
        ObjectiveRegistry registry = seasons.objectiveRegistry();
        for (ObjectiveDefinition objective : season.objectives) {
            ObjectiveHandler handler = registry.handler(objective.type).orElse(null);
            if (handler == null || handler.manual()) {
                continue;
            }
            int amount = handler.progressFor(event, objective);
            if (amount > 0) {
                seasons.addObjectiveProgress(uuid, season.id, objective.id, amount);
            }
        }
    }
}
