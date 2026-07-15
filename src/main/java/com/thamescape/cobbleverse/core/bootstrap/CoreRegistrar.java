package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.api.CobbleverseRegistrar;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.game.GameEventBus;
import com.thamescape.cobbleverse.core.game.GameEventListener;
import com.thamescape.cobbleverse.core.reward.currency.CurrencyProvider;
import com.thamescape.cobbleverse.core.reward.currency.CurrencyService;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveRegistry;

/**
 * The core's {@link CobbleverseRegistrar}: routes extension registrations straight into the live
 * registries. Tracks a running count so startup can log how much extensions contributed.
 */
final class CoreRegistrar implements CobbleverseRegistrar {

    private final ObjectiveRegistry objectives;
    private final GameEventBus gameEvents;
    private final HealthCheckService health;
    private final CurrencyService currencies;
    private int registrations;

    CoreRegistrar(ObjectiveRegistry objectives, GameEventBus gameEvents, HealthCheckService health,
                  CurrencyService currencies) {
        this.objectives = objectives;
        this.gameEvents = gameEvents;
        this.health = health;
        this.currencies = currencies;
    }

    @Override
    public CobbleverseRegistrar objectiveHandler(ObjectiveHandler handler) {
        objectives.register(handler);
        registrations++;
        return this;
    }

    @Override
    public CobbleverseRegistrar gameEventListener(GameEventListener listener) {
        gameEvents.register(listener);
        registrations++;
        return this;
    }

    @Override
    public CobbleverseRegistrar healthCheck(HealthCheck check) {
        health.register(check);
        registrations++;
        return this;
    }

    @Override
    public CobbleverseRegistrar currencyProvider(CurrencyProvider provider) {
        currencies.register(provider);
        registrations++;
        return this;
    }

    int registrations() {
        return registrations;
    }
}
