package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 0.5.1: track whether a completed event has finished distributing its rewards, so a crash partway
 * through rewarding participants can be resumed on the next startup.
 *
 * <p>Existing rows default to {@code 1} (already distributed) — pre-0.5.1 completions ran to
 * completion synchronously, so they must not be re-distributed on upgrade.
 */
public final class V006EventRewardState implements Migration {

    @Override
    public int version() {
        return 6;
    }

    @Override
    public String name() {
        return "event_reward_state";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE events ADD COLUMN rewards_distributed INTEGER NOT NULL DEFAULT 1");
        }
    }
}
