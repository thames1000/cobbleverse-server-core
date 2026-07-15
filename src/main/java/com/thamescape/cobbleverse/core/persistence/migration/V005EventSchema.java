package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** 0.5.0 schema: event lifecycle state and per-player participation (with score for leaderboards). */
public final class V005EventSchema implements Migration {

    @Override
    public int version() {
        return 5;
    }

    @Override
    public String name() {
        return "event_schema";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS events (
                        event_id   TEXT    PRIMARY KEY,
                        state      TEXT    NOT NULL,
                        started_at INTEGER,
                        ended_at   INTEGER,
                        updated_at INTEGER NOT NULL
                    )
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS event_participation (
                        event_id  TEXT    NOT NULL,
                        uuid      TEXT    NOT NULL,
                        joined_at INTEGER NOT NULL,
                        score     INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (event_id, uuid)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_event_participation_leaderboard "
                    + "ON event_participation(event_id, score DESC)");
        }
    }
}
