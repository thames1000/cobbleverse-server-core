package com.thamescape.cobbleverse.core.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Opens connections to a specific database backend. Abstracts the backend so PostgreSQL / MariaDB
 * providers can be added later without touching {@link DatabaseManager}.
 */
public interface DatabaseProvider {

    /** Backend id, e.g. {@code sqlite}. */
    String type();

    /** Opens a ready-to-use connection (driver loaded, pragmas applied). */
    Connection connect() throws SQLException;

    /** Human-readable description for reports (e.g. the file path). */
    String describe();
}
