package com.thamescape.cobbleverse.core.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that returns nothing and may throw {@link SQLException}. */
@FunctionalInterface
public interface SqlConsumer {
    void accept(Connection connection) throws SQLException;
}
