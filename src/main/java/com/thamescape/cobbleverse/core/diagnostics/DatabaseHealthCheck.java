package com.thamescape.cobbleverse.core.diagnostics;

import com.thamescape.cobbleverse.core.persistence.DatabaseManager;

/** Reports whether the database connection is open and how many writes are queued. */
public final class DatabaseHealthCheck implements HealthCheck {

    private final DatabaseManager db;

    public DatabaseHealthCheck(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public String name() {
        return "Database";
    }

    @Override
    public HealthCheckResult run() {
        if (!db.isConnected()) {
            return HealthCheckResult.error(name(), "not connected");
        }
        int pending = db.pending();
        if (pending > 100) {
            return HealthCheckResult.warn(name(), pending + " writes queued (backlog)");
        }
        return HealthCheckResult.ok(name(), pending + " pending");
    }
}
