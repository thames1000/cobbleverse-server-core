package com.thamescape.cobbleverse.core.diagnostics;

import com.thamescape.cobbleverse.core.integration.IntegrationManager;
import com.thamescape.cobbleverse.core.integration.IntegrationReport;
import com.thamescape.cobbleverse.core.integration.IntegrationStatus;

import java.util.List;

/** Reports OK unless an integration errored during detection. Missing optional mods are fine. */
public final class IntegrationHealthCheck implements HealthCheck {

    private final IntegrationManager integrations;

    public IntegrationHealthCheck(IntegrationManager integrations) {
        this.integrations = integrations;
    }

    @Override
    public String name() {
        return "Integrations";
    }

    @Override
    public HealthCheckResult run() {
        List<IntegrationReport> reports = integrations.reports();
        long errored = reports.stream()
                .filter(r -> r.status() == IntegrationStatus.ERROR)
                .count();
        long available = reports.stream().filter(IntegrationReport::available).count();
        if (errored > 0) {
            return HealthCheckResult.warn(name(), errored + " integration(s) errored during detection");
        }
        return HealthCheckResult.ok(name(), available + " of " + reports.size() + " available");
    }
}
