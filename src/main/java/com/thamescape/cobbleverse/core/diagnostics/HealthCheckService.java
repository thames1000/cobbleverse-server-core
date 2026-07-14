package com.thamescape.cobbleverse.core.diagnostics;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs all registered health checks and aggregates their results. Backs the {@code /cvcore health}
 * command. New subsystems register a {@link HealthCheck} as they come online (database, scheduler,
 * ...).
 */
public final class HealthCheckService {

    private final List<HealthCheck> checks = new ArrayList<>();

    public void register(HealthCheck check) {
        checks.add(check);
    }

    /** Runs every check. A check that throws is reported as an ERROR rather than propagating. */
    public List<HealthCheckResult> runAll() {
        List<HealthCheckResult> results = new ArrayList<>(checks.size());
        for (HealthCheck check : checks) {
            try {
                results.add(check.run());
            } catch (Exception e) {
                results.add(HealthCheckResult.error(check.name(), "check threw: " + e.getMessage()));
            }
        }
        return results;
    }

    /** The worst status across all checks, treating no checks as OK. */
    public HealthStatus overall(List<HealthCheckResult> results) {
        HealthStatus worst = HealthStatus.OK;
        for (HealthCheckResult r : results) {
            if (r.status() == HealthStatus.ERROR) {
                return HealthStatus.ERROR;
            }
            if (r.status() == HealthStatus.WARN) {
                worst = HealthStatus.WARN;
            }
        }
        return worst;
    }
}
