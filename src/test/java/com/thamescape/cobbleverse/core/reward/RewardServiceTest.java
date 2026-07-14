package com.thamescape.cobbleverse.core.reward;

import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigLoader;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.RewardRepository;
import com.thamescape.cobbleverse.core.reward.type.ItemRewardHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the reward pipeline paths that don't need a live server: the offline queue, claim dedup, and
 * previews. (Item/command execution needs a running server and is covered by manual verification.)
 */
class RewardServiceTest {

    @TempDir
    Path tmp;

    private RewardService service(DatabaseManager db) {
        ConfigManager config = new ConfigManager(new ConfigLoader(tmp.resolve("config")));
        config.load(); // writes defaults, including the sample_tier_1 reward definition
        RewardRegistry registry = new RewardRegistry();
        registry.register(new ItemRewardHandler());
        return new RewardService(config, db, new RewardRepository(), registry, new AuditService(true));
    }

    private DatabaseManager open() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);
        return db;
    }

    @Test
    void offlineGrantIsQueued() {
        DatabaseManager db = open();
        RewardService rewards = service(db);
        RewardRepository repo = new RewardRepository();
        UUID uuid = UUID.randomUUID();
        try {
            // No running server in a unit test => the player is treated as offline and the reward queues.
            RewardResult result = rewards.grant(uuid, "sample_tier_1", "test");
            assertEquals(RewardStatus.QUEUED, result.status());
            assertEquals(1L, db.callSync(repo::queueCount));
            assertEquals(1, db.callSync(conn -> repo.findQueued(conn, uuid)).size());
        } finally {
            db.close();
        }
    }

    @Test
    void alreadyClaimedIsReported() {
        DatabaseManager db = open();
        RewardService rewards = service(db);
        RewardRepository repo = new RewardRepository();
        UUID uuid = UUID.randomUUID();
        try {
            // Simulate a prior claim of this non-repeatable definition.
            boolean firstInsert = db.callSync(conn ->
                    repo.insertClaimIfAbsent(conn, uuid, "sample_tier_1", 1L, "prior"));
            assertTrue(firstInsert);
            // Second insert is deduped by the primary key.
            boolean secondInsert = db.callSync(conn ->
                    repo.insertClaimIfAbsent(conn, uuid, "sample_tier_1", 2L, "prior"));
            assertFalse(secondInsert);

            RewardResult result = rewards.grant(uuid, "sample_tier_1", "test");
            assertEquals(RewardStatus.ALREADY_CLAIMED, result.status());
            assertEquals(0L, db.callSync(repo::queueCount), "already-claimed reward must not queue");
        } finally {
            db.close();
        }
    }

    @Test
    void unknownDefinitionIsReported() {
        DatabaseManager db = open();
        RewardService rewards = service(db);
        try {
            assertEquals(RewardStatus.UNKNOWN, rewards.grant(UUID.randomUUID(), "nope", "test").status());
        } finally {
            db.close();
        }
    }

    @Test
    void previewDescribesEntries() {
        DatabaseManager db = open();
        RewardService rewards = service(db);
        try {
            RewardResult preview = rewards.preview(UUID.randomUUID(), "sample_tier_1");
            // The sample definition has two entries (an item and a currency).
            assertEquals(2, preview.lines().size());
            assertTrue(preview.lines().stream().anyMatch(l -> l.contains("diamond")));
        } finally {
            db.close();
        }
    }

    @Test
    void queueRoundTrip() {
        DatabaseManager db = open();
        RewardRepository repo = new RewardRepository();
        UUID uuid = UUID.randomUUID();
        try {
            db.runSync(conn -> repo.queue(conn, uuid, "sample_tier_1", 1L, "test"));
            var queued = db.callSync(conn -> repo.findQueued(conn, uuid));
            assertEquals(1, queued.size());
            db.runSync(conn -> repo.deleteQueued(conn, queued.get(0).id()));
            assertTrue(db.callSync(conn -> repo.findQueued(conn, uuid)).isEmpty());
        } finally {
            db.close();
        }
    }
}
