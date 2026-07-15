package com.thamescape.cobbleverse.core.integration.cobblemon;

import com.thamescape.cobbleverse.core.game.GameEventBus;
import com.thamescape.cobbleverse.core.game.battle.BattleWonGameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Translates Cobblemon's game events into core {@link com.thamescape.cobbleverse.core.game.GameEvent}s
 * on the {@link GameEventBus}. This is the <b>only</b> class that knows about Cobblemon — nothing else
 * in the core imports it, so a missing or different Cobblemon degrades gracefully.
 *
 * <p><b>Wiring status (0.6.0):</b> detection and the publish seam ({@link #publishCapture},
 * {@link #publishBattleWon}) are in place. The concrete subscription to Cobblemon's capture/battle
 * events must be compiled against Cobblemon's (Kotlin) event API for a specific Cobblemon version, so
 * it is added as a follow-up once that version is fixed. Until then, use {@code /cvcore debug publish}
 * to exercise the whole pipeline (bus → listeners) without Cobblemon.
 */
public final class CobblemonGameEventAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/GAME");
    private static final String COBBLEMON_MOD_ID = "cobblemon";

    private final GameEventBus bus;

    public CobblemonGameEventAdapter(GameEventBus bus) {
        this.bus = bus;
    }

    public void register() {
        if (!FabricLoader.getInstance().isModLoaded(COBBLEMON_MOD_ID)) {
            LOGGER.info("Cobblemon not present; game-event bridge idle");
            return;
        }
        // Cobblemon's event API (CobblemonEvents.POKEMON_CAPTURED, ...) is subscribed here in the
        // Cobblemon-compiled adapter build, calling publishCapture/publishBattleWon below.
        LOGGER.info("Cobblemon detected; game-event bridge ready (capture/battle subscription pending "
                + "the Cobblemon-compiled adapter — see docs/game-events.md)");
    }

    /** Publishes a capture to the bus. Called by the Cobblemon event hook (or the debug command). */
    public void publishCapture(UUID player, String species, boolean shiny) {
        bus.publish(new PokemonCapturedGameEvent(player, Instant.now(), species, shiny));
    }

    /** Publishes a battle win to the bus. Called by the Cobblemon event hook. */
    public void publishBattleWon(UUID player) {
        bus.publish(new BattleWonGameEvent(player, Instant.now()));
    }
}
