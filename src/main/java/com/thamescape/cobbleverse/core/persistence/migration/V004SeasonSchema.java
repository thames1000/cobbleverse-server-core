package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** 0.4.0 schema: per-player season points, per-objective progress, and season lifecycle tracking. */
public final class V004SeasonSchema implements Migration {

    @Override
    public int version() {
        return 4;
    }

    @Override
    public String name() {
        return "season_schema";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS season_progress (
                        uuid      TEXT    NOT NULL,
                        season_id TEXT    NOT NULL,
                        points    INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, season_id)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_season_progress_leaderboard "
                    + "ON season_progress(season_id, points DESC)");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS objective_progress (
                        uuid         TEXT    NOT NULL,
                        season_id    TEXT    NOT NULL,
                        objective_id TEXT    NOT NULL,
                        progress     INTEGER NOT NULL DEFAULT 0,
                        completed    INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, season_id, objective_id)
                    )
                    """);

            // Last-known lifecycle state per season, so start/end transitions fire exactly once.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS season_lifecycle (
                        season_id  TEXT    PRIMARY KEY,
                        state      TEXT    NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }
}
