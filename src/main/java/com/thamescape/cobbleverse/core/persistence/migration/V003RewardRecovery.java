package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 0.3.1 recovery schema: per-entry reward results (so a partial grant can retry only the entries that
 * failed), a claim completion status, and queue delivery bookkeeping (attempts, last error, dead-letter
 * status).
 */
public final class V003RewardRecovery implements Migration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String name() {
        return "reward_recovery";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Existing claims (pre-0.3.1) were "presence == complete", so default them to COMPLETE.
            // New claims are inserted explicitly as PARTIAL and promoted once all entries succeed.
            st.execute("ALTER TABLE reward_claims ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETE'");

            // One row per (player, definition, entry index): the outcome of that specific entry, so a
            // retry can skip entries already granted.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS reward_entry_results (
                        uuid          TEXT    NOT NULL,
                        definition_id TEXT    NOT NULL,
                        entry_index   INTEGER NOT NULL,
                        status        TEXT    NOT NULL,
                        last_error    TEXT,
                        updated_at    INTEGER NOT NULL,
                        PRIMARY KEY (uuid, definition_id, entry_index)
                    )
                    """);

            // Queue delivery bookkeeping: retried rewards accumulate attempts and dead-letter after a
            // configured maximum instead of being silently dropped.
            st.execute("ALTER TABLE reward_queue ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE reward_queue ADD COLUMN last_attempt_at INTEGER");
            st.execute("ALTER TABLE reward_queue ADD COLUMN last_error TEXT");
            st.execute("ALTER TABLE reward_queue ADD COLUMN status TEXT NOT NULL DEFAULT 'pending'");
        }
    }
}
