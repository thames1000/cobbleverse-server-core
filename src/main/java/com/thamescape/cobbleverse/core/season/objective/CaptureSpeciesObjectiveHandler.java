package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;

/** Progresses when a specific species is captured (objective field {@code species}). */
public final class CaptureSpeciesObjectiveHandler implements ObjectiveHandler {

    @Override
    public String type() {
        return "capture_species";
    }

    @Override
    public int progressFor(GameEvent event, ObjectiveDefinition objective) {
        if (event instanceof PokemonCapturedGameEvent capture && objective.species != null
                && objective.species.equalsIgnoreCase(capture.species())) {
            return 1;
        }
        return 0;
    }
}
