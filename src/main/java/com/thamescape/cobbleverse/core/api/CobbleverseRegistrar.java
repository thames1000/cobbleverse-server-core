package com.thamescape.cobbleverse.core.api;

import com.thamescape.cobbleverse.core.diagnostics.HealthCheck;
import com.thamescape.cobbleverse.core.game.GameEventListener;
import com.thamescape.cobbleverse.core.reward.currency.CurrencyProvider;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveHandler;

/**
 * Handed to each {@link CobbleverseExtension} at startup so it can plug into the core's open registries.
 * Every method returns {@code this} for chaining. Registrations take effect immediately; objective
 * handlers registered here are visible to config validation, which runs after all extensions have been
 * offered the registrar.
 *
 * <p><b>Experimental (0.8.0):</b> this surface may change until it is frozen in 1.0.
 */
public interface CobbleverseRegistrar {

    /** Registers a season objective type (its {@link ObjectiveHandler#type()} becomes a valid config type). */
    CobbleverseRegistrar objectiveHandler(ObjectiveHandler handler);

    /** Subscribes a listener to the game-event bus (captures, battles, joins, and custom events). */
    CobbleverseRegistrar gameEventListener(GameEventListener listener);

    /** Adds a diagnostic surfaced by {@code /cvcore health} and the web API's health endpoint. */
    CobbleverseRegistrar healthCheck(HealthCheck check);

    /** Registers a currency backend addressable by {@link CurrencyProvider#id()} in reward configs. */
    CobbleverseRegistrar currencyProvider(CurrencyProvider provider);
}
