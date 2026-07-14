package com.thamescape.cobbleverse.core.persistence;

import com.thamescape.cobbleverse.core.persistence.migration.V001InitialSchema;
import com.thamescape.cobbleverse.core.persistence.migration.V002RewardsAndCurrency;
import com.thamescape.cobbleverse.core.persistence.repository.RewardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the real 0.3.0 → 0.3.1 upgrade: a database left at schema v2 with an existing reward claim
 * is migrated by V003, and that legacy claim is treated as COMPLETE (old semantics were
 * "presence == complete").
 */
class MigrationUpgradeTest {

    @TempDir
    Path tmp;

    @Test
    void v003BackfillsExistingClaimsAsComplete() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();

        // Simulate a database created by 0.3.0 (schema v2), with a claim in the old shape (no status).
        new MigrationManager(List.of(new V001InitialSchema(), new V002RewardsAndCurrency())).migrate(db);
        UUID uuid = UUID.randomUUID();
        db.runSync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO reward_claims(uuid, definition_id, claimed_at, source) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "legacy_reward");
                ps.setLong(3, 1L);
                ps.setString(4, "legacy");
                ps.executeUpdate();
            }
        });

        // Upgrade to the current schema (applies V003).
        MigrationManager current = MigrationManager.withDefaults();
        current.migrate(db);
        assertEquals(current.latestVersion(), current.currentVersion(db));

        RewardRepository repo = new RewardRepository();
        boolean complete = db.callSync(conn -> repo.isComplete(conn, uuid, "legacy_reward"));
        try {
            assertTrue(complete, "pre-0.3.1 claims must be treated as COMPLETE after V003");
        } finally {
            db.close();
        }
    }
}
