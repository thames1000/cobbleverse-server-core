package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.api.CobbleverseExtension;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheck;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckResult;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.game.GameEventBus;
import com.thamescape.cobbleverse.core.reward.currency.CurrencyService;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The extension phase: registrar routing into the live registries, and isolation of broken extensions. */
class ExtensionLoaderTest {

    private static CoreRegistrar registrar(ObjectiveRegistry objectives, GameEventBus bus,
                                           HealthCheckService health) {
        return new CoreRegistrar(objectives, bus, health, new CurrencyService(new AuditService(false)));
    }

    private static HealthCheck check(String name) {
        return new HealthCheck() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public HealthCheckResult run() {
                return HealthCheckResult.ok(name);
            }
        };
    }

    @Test
    void appliesRegistrationsToTheLiveRegistries() {
        ObjectiveRegistry objectives = new ObjectiveRegistry();
        GameEventBus bus = new GameEventBus();
        HealthCheckService health = new HealthCheckService();
        CoreRegistrar registrar = registrar(objectives, bus, health);

        CobbleverseExtension extension = r -> r
                .objectiveHandler(() -> "weekly_login")
                .gameEventListener(event -> { })
                .healthCheck(check("ext-check"));
        ExtensionLoader.apply(List.of(extension), registrar);

        assertTrue(objectives.isKnown("weekly_login"), "the extension's objective type is registered");
        assertEquals(3, registrar.registrations(), "all three registrations were routed");
        assertTrue(health.runAll().stream().anyMatch(result -> result.name().equals("ext-check")),
                "the extension's health check is surfaced");
    }

    @Test
    void isolatesAThrowingExtension() {
        ObjectiveRegistry objectives = new ObjectiveRegistry();
        CoreRegistrar registrar = registrar(objectives, new GameEventBus(), new HealthCheckService());

        CobbleverseExtension broken = r -> {
            throw new RuntimeException("boom");
        };
        CobbleverseExtension good = r -> r.objectiveHandler(() -> "ok");

        assertDoesNotThrow(() -> ExtensionLoader.apply(List.of(broken, good), registrar),
                "one broken extension must not abort the phase");
        assertTrue(objectives.isKnown("ok"), "a good extension after a broken one still applies");
    }
}
