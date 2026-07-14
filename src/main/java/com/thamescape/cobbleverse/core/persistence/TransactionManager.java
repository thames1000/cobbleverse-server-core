package com.thamescape.cobbleverse.core.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs a unit of work inside a single database transaction: disables autocommit, commits on success,
 * rolls back on any exception, and restores autocommit afterwards.
 *
 * <p>Used for operations that must be all-or-nothing (reward claims from 0.3.0). Callers on the
 * database worker thread invoke {@link #execute} directly; most code goes through
 * {@link DatabaseManager#runInTransactionAsync}.
 */
public final class TransactionManager {

    private TransactionManager() {
    }

    public static void execute(Connection connection, SqlConsumer work) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            work.accept(connection);
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
