package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 0.6.1 hardening: a durable outbox for milestone reward grants. When an objective completion crosses
 * a points milestone, the owed grant is recorded here in the <b>same transaction</b> as the objective
 * and points. A server-thread delivery step then grants it and deletes the row; anything left is
 * resumed on startup — so a crash after committing points can no longer lose the reward.
 */
public final class V008PendingMilestones implements Migration {

    @Override
    public int version() {
        return 8;
    }

    @Override
    public String name() {
        return "pending_milestone_rewards";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS pending_milestone_rewards (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid       TEXT    NOT NULL,
                        season_id  TEXT    NOT NULL,
                        reward_id  TEXT    NOT NULL,
                        created_at INTEGER NOT NULL,
                        UNIQUE (uuid, season_id, reward_id)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_pending_milestone_uuid "
                    + "ON pending_milestone_rewards(uuid)");
        }
    }
}
