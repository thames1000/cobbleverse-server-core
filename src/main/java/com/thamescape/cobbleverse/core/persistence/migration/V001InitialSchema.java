package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initial schema for 0.2.0: player profiles and the audit log. Later systems (seasons, rewards,
 * events, currencies) add their own tables in their own migrations.
 */
public final class V001InitialSchema implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String name() {
        return "initial_schema";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        uuid             TEXT    PRIMARY KEY,
                        last_known_name  TEXT,
                        first_joined_at  INTEGER NOT NULL,
                        last_joined_at   INTEGER NOT NULL,
                        playtime_seconds INTEGER NOT NULL DEFAULT 0
                    )
                    """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_player_profiles_name "
                    + "ON player_profiles(last_known_name)");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id             INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp      INTEGER NOT NULL,
                        action         TEXT    NOT NULL,
                        actor          TEXT,
                        actor_name     TEXT,
                        target         TEXT,
                        source         TEXT    NOT NULL,
                        context        TEXT,
                        before_value   TEXT,
                        after_value    TEXT,
                        success        INTEGER NOT NULL,
                        failure_reason TEXT
                    )
                    """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log(timestamp)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action)");
        }
    }
}
