package com.thamescape.cobbleverse.core.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 0.3.0 schema: reward claims, the offline reward queue, and internal currency balances plus their
 * transaction ledger.
 */
public final class V002RewardsAndCurrency implements Migration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String name() {
        return "rewards_and_currency";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            // One row per player per non-repeatable definition. The primary key enforces "claim once".
            st.execute("""
                    CREATE TABLE IF NOT EXISTS reward_claims (
                        uuid          TEXT    NOT NULL,
                        definition_id TEXT    NOT NULL,
                        claimed_at    INTEGER NOT NULL,
                        source        TEXT    NOT NULL,
                        PRIMARY KEY (uuid, definition_id)
                    )
                    """);

            // Rewards queued for offline players, delivered on next join.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS reward_queue (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid          TEXT    NOT NULL,
                        definition_id TEXT    NOT NULL,
                        queued_at     INTEGER NOT NULL,
                        source        TEXT    NOT NULL
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_reward_queue_uuid ON reward_queue(uuid)");

            // Core-owned (internal) currency balances, stored as exact decimal strings.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS currency_balances (
                        uuid     TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        balance  TEXT NOT NULL,
                        PRIMARY KEY (uuid, currency)
                    )
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS currency_transactions (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid       TEXT    NOT NULL,
                        currency   TEXT    NOT NULL,
                        amount     TEXT    NOT NULL,
                        type       TEXT    NOT NULL,
                        timestamp  INTEGER NOT NULL,
                        reason     TEXT
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_currency_tx_uuid ON currency_transactions(uuid)");
        }
    }
}
