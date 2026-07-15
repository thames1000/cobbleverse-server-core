package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.game.battle.BattleWonGameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the event-driven objective handlers' matching logic. Pure, no Minecraft. */
class ObjectiveHandlerTest {

    private static PokemonCapturedGameEvent capture(String species, boolean shiny) {
        return new PokemonCapturedGameEvent(UUID.randomUUID(), Instant.now(), species, shiny);
    }

    private static BattleWonGameEvent battle(String kind, boolean wild) {
        return new BattleWonGameEvent(UUID.randomUUID(), Instant.now(), kind, "singles", wild);
    }

    private static ObjectiveDefinition objective(String species, String battleKind) {
        ObjectiveDefinition def = new ObjectiveDefinition();
        def.species = species;
        def.battleKind = battleKind;
        return def;
    }

    @Test
    void captureSpeciesMatchesOnlyThatSpecies() {
        var handler = new CaptureSpeciesObjectiveHandler();
        assertEquals(1, handler.progressFor(capture("pikachu", false), objective("Pikachu", null)));
        assertEquals(0, handler.progressFor(capture("bulbasaur", false), objective("pikachu", null)));
        assertEquals(0, handler.progressFor(battle("pvp", false), objective("pikachu", null)));
    }

    @Test
    void captureShinyMatchesShinyOnly() {
        var handler = new CaptureShinyObjectiveHandler();
        assertEquals(1, handler.progressFor(capture("magikarp", true), objective(null, null)));
        assertEquals(0, handler.progressFor(capture("magikarp", false), objective(null, null)));
    }

    @Test
    void captureAnyMatchesAnyCapture() {
        var handler = new CaptureAnyObjectiveHandler();
        assertEquals(1, handler.progressFor(capture("anything", false), objective(null, null)));
        assertEquals(0, handler.progressFor(battle("pvw", false), objective(null, null)));
    }

    @Test
    void battleWonExcludesWildCapturesAndFiltersKind() {
        var handler = new BattleWonObjectiveHandler();
        // Any non-wild win when battleKind is blank.
        assertEquals(1, handler.progressFor(battle("pvn", false), objective(null, null)));
        // Wild capture (Cobblemon reports as a battle victory) must not count.
        assertEquals(0, handler.progressFor(battle("pvw", true), objective(null, null)));
        // Kind filter.
        assertEquals(1, handler.progressFor(battle("pvp", false), objective(null, "pvp")));
        assertEquals(0, handler.progressFor(battle("pvn", false), objective(null, "pvp")));
    }
}
