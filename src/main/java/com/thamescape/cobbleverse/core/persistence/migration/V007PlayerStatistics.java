package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 0.6.1 schema: per-player statistics as a flexible key/value table, so new stats (captures, shinies,
 * battles won, ...) can be added without further migrations.
 */
public final class V007PlayerStatistics implements Migration {

    @Override
    public int version() {
        return 7;
    }

    @Override
    public String name() {
        return "player_statistics";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_statistics (
                        uuid  TEXT    NOT NULL,
                        stat  TEXT    NOT NULL,
                        value INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, stat)
                    )
                    """);
        }
    }
}
