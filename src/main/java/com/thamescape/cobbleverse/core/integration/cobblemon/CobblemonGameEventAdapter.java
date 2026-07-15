package com.thamescape.cobbleverse.core.integration.cobblemon;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.thamescape.cobbleverse.core.game.GameEventBus;
import com.thamescape.cobbleverse.core.game.battle.BattleWonGameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Translates Cobblemon's events into core {@link com.thamescape.cobbleverse.core.game.GameEvent}s on
 * the {@link GameEventBus}. This is the <b>only</b> class that imports Cobblemon; it is compiled
 * against Cobblemon ({@code modCompileOnly}) but Cobblemon is never bundled and not required at
 * runtime. It is instantiated only when Cobblemon is actually installed (the bootstrap gates on the
 * Fabric mod list), so the core runs standalone without it.
 *
 * <p>Subscriptions live for the server's lifetime (Fabric has no hot-unload), so there is no teardown.
 */
public final class CobblemonGameEventAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/GAME");

    private final GameEventBus bus;

    public CobblemonGameEventAdapter(GameEventBus bus) {
        this.bus = bus;
    }

    /** Subscribes to Cobblemon's capture and battle-victory events. Called only when Cobblemon is present. */
    public void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(this::onCapture);
        CobblemonEvents.BATTLE_VICTORY.subscribe(this::onBattleVictory);
        LOGGER.info("Cobblemon game-event bridge active (capture, battle victory)");
    }

    private void onCapture(PokemonCapturedEvent event) {
        ServerPlayerEntity player = event.getPlayer();
        if (player == null) {
            return;
        }
        bus.publish(new PokemonCapturedGameEvent(player.getUuid(), Instant.now(),
                event.getPokemon().getSpecies().getName(), event.getPokemon().getShiny()));
    }

    private void onBattleVictory(BattleVictoryEvent event) {
        String kind = event.getBattle().isPvP() ? "pvp"
                : event.getBattle().isPvN() ? "pvn"
                : event.getBattle().isPvW() ? "pvw" : "other";
        // Normalize Cobblemon's format name to a stable, lowercase value for consumers.
        String format = event.getBattle().getFormat().getBattleType().getName().toLowerCase(Locale.ROOT);
        boolean wildCapture = event.getWasWildCapture();
        for (BattleActor winner : event.getWinners()) {
            for (UUID uuid : winner.getPlayerUUIDs()) {
                bus.publish(new BattleWonGameEvent(uuid, Instant.now(), kind, format, wildCapture));
            }
        }
    }
}
