package com.thamescape.cobbleverse.core.diagnostics;

import com.thamescape.cobbleverse.core.integration.IntegrationManager;

/**
 * Reports whether a permission provider is present. Absence is a warning, not an error, because the
 * operator-level fallback keeps admin commands working without one.
 */
public final class PermissionHealthCheck implements HealthCheck {

    private final IntegrationManager integrations;

    public PermissionHealthCheck(IntegrationManager integrations) {
        this.integrations = integrations;
    }

    @Override
    public String name() {
        return "Permissions";
    }

    @Override
    public HealthCheckResult run() {
        if (integrations.isAvailable("luckperms")) {
            return HealthCheckResult.ok(name(), "LuckPerms provider active");
        }
        return HealthCheckResult.warn(name(), "no permission provider; using operator-level fallback");
    }
}
