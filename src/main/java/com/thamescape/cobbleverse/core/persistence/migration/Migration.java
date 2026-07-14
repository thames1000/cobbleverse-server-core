package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A single, ordered, forward-only schema change. Each migration is applied at most once and recorded
 * in the {@code schema_version} table. Migrations must be additive and must never assume they run
 * more than once.
 */
public interface Migration {

    /** Monotonic version. Migrations apply in ascending order; gaps are allowed. */
    int version();

    /** Short human-readable name for logs and the {@code schema_version} table. */
    String name();

    /** Applies the change. Runs inside a transaction; throwing rolls it back. */
    void apply(Connection connection) throws SQLException;
}
