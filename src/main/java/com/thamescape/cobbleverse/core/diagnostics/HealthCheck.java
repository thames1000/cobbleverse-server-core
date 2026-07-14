package com.thamescape.cobbleverse.core.diagnostics;

/** A single diagnostic probe. Implementations must be fast and must not throw. */
public interface HealthCheck {

    String name();

    HealthCheckResult run();
}
