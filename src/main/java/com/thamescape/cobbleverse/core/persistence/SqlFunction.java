package com.thamescape.cobbleverse.core.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that returns a result and may throw {@link SQLException}. */
@FunctionalInterface
public interface SqlFunction<T> {
    T apply(Connection connection) throws SQLException;
}
