package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.GameEventListener;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;
import com.thamescape.cobbleverse.core.season.SeasonDefinition;
import com.thamescape.cobbleverse.core.season.SeasonService;

import java.util.ArrayList;
import java.util.List;
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
        // Match handlers on the current (server) thread — cheap, pure, no database. Any actual
        // database/reward work is handed off to advanceObjectivesAsync so the tick never blocks.
        ObjectiveRegistry registry = seasons.objectiveRegistry();
        List<SeasonService.ObjectiveMatch> matches = new ArrayList<>();
        for (ObjectiveDefinition objective : season.objectives) {
            ObjectiveHandler handler = registry.handler(objective.type).orElse(null);
            if (handler == null || handler.manual()) {
                continue;
            }
            int amount = handler.progressFor(event, objective);
            if (amount > 0) {
                matches.add(new SeasonService.ObjectiveMatch(objective.id, amount));
            }
        }
        seasons.advanceObjectivesAsync(uuid, season.id, matches);
    }
}
