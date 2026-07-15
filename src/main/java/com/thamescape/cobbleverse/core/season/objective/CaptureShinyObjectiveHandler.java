package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;

/** Progresses when any shiny Pokémon is captured. */
public final class CaptureShinyObjectiveHandler implements ObjectiveHandler {

    @Override
    public String type() {
        return "capture_shiny";
    }

    @Override
    public int progressFor(GameEvent event, ObjectiveDefinition objective) {
        return event instanceof PokemonCapturedGameEvent capture && capture.shiny() ? 1 : 0;
    }
}
