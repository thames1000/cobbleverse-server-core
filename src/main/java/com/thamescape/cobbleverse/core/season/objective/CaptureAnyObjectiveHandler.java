package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;

/** Progresses on any Pokémon capture. */
public final class CaptureAnyObjectiveHandler implements ObjectiveHandler {

    @Override
    public String type() {
        return "capture_any";
    }

    @Override
    public int progressFor(GameEvent event, ObjectiveDefinition objective) {
        return event instanceof PokemonCapturedGameEvent ? 1 : 0;
    }
}
