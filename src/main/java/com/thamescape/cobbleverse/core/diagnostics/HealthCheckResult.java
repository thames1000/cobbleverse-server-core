package com.thamescape.cobbleverse.core.diagnostics;

/** Result of a single health check. */
public record HealthCheckResult(String name, HealthStatus status, String detail) {

    public static HealthCheckResult ok(String name) {
        return new HealthCheckResult(name, HealthStatus.OK, "OK");
    }

    public static HealthCheckResult ok(String name, String detail) {
        return new HealthCheckResult(name, HealthStatus.OK, detail);
    }

    public static HealthCheckResult warn(String name, String detail) {
        return new HealthCheckResult(name, HealthStatus.WARN, detail);
    }

    public static HealthCheckResult error(String name, String detail) {
        return new HealthCheckResult(name, HealthStatus.ERROR, detail);
    }
}
