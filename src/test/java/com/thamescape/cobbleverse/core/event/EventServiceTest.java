package com.thamescape.cobbleverse.core.event;

import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigLoader;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.EventRepository;
import com.thamescape.cobbleverse.core.persistence.repository.RewardRepository;
import com.thamescape.cobbleverse.core.reward.RewardRegistry;
import com.thamescape.cobbleverse.core.reward.RewardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the event lifecycle state machine, participation, scoring/leaderboard, and reward
 * distribution on completion (queued, since there's no live server). Uses the default {@code
 * events.json} sample event, which grants {@code sample_tier_1} on completion.
 */
class EventServiceTest {

    private static final String EVENT = "sample_event";

    @TempDir
    Path tmp;

    private DatabaseManager db;
    private EventService events;
    private RewardRepository rewardRepo;

    private EventService build() {
        db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);
        ConfigManager config = new ConfigManager(new ConfigLoader(tmp.resolve("config")));
        config.load(); // creates default events.json (sample_event -> sample_tier_1) and rewards.json
        AuditService audit = new AuditService(true);
        RewardService rewards = new RewardService(config, db, new RewardRepository(),
                new RewardRegistry(), audit);
        rewardRepo = new RewardRepository();
        return new EventService(config, db, new EventRepository(), rewards, audit);
    }

    @Test
    void lifecycleTransitions() {
        events = build();
        try {
            assertEquals(EventState.DRAFT, events.state(EVENT));
            assertFalse(events.transition(EVENT, EventState.ACTIVE, "t").ok(), "DRAFT cannot jump to ACTIVE");

            assertTrue(events.transition(EVENT, EventState.OPEN, "t").ok());
            assertTrue(events.transition(EVENT, EventState.ACTIVE, "t").ok());
            assertEquals(EventState.ACTIVE, events.state(EVENT));
            assertTrue(events.transition(EVENT, EventState.COMPLETED, "t").ok());
            assertEquals(EventState.COMPLETED, events.state(EVENT));

            assertFalse(events.transition(EVENT, EventState.CANCELLED, "t").ok(), "COMPLETED is terminal");
        } finally {
            db.close();
        }
    }

    @Test
    void cannotJoinBeforeOpen() {
        events = build();
        try {
            assertFalse(events.join(UUID.randomUUID(), EVENT).ok(), "cannot join a DRAFT event");
        } finally {
            db.close();
        }
    }

    @Test
    void joinThenCompletionQueuesReward() {
        events = build();
        UUID uuid = UUID.randomUUID();
        try {
            events.transition(EVENT, EventState.OPEN, "t");
            assertTrue(events.join(uuid, EVENT).ok());
            assertEquals(1, events.participantCount(EVENT));

            events.transition(EVENT, EventState.ACTIVE, "t");
            events.transition(EVENT, EventState.COMPLETED, "t");
            // sample_event grants sample_tier_1 to each participant; offline, so it queues.
            assertEquals(1L, db.callSync(rewardRepo::queueCount), "completion should queue the reward");
        } finally {
            db.close();
        }
    }

    @Test
    void leaderboardOrdersByScore() {
        events = build();
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        try {
            events.transition(EVENT, EventState.OPEN, "t");
            events.join(low, EVENT);
            events.join(high, EVENT);
            assertTrue(events.addScore(low, EVENT, 5).ok());
            assertTrue(events.addScore(high, EVENT, 10).ok());

            List<EventParticipant> board = events.leaderboard(EVENT, 10);
            assertEquals(2, board.size());
            assertEquals(high, board.get(0).uuid(), "highest score is first");
            assertEquals(10, board.get(0).score());

            assertFalse(events.addScore(UUID.randomUUID(), EVENT, 5).ok(), "non-participant can't score");
        } finally {
            db.close();
        }
    }

    @Test
    void addScoreIsAtomicAndClamps() {
        events = build();
        EventRepository repo = new EventRepository();
        UUID uuid = UUID.randomUUID();
        try {
            db.runSync(conn -> repo.join(conn, EVENT, uuid, 1L));

            var first = db.callSync(conn -> repo.addScore(conn, EVENT, uuid, 5)).orElseThrow();
            assertEquals(0, first.oldScore());
            assertEquals(5, first.newScore());

            var second = db.callSync(conn -> repo.addScore(conn, EVENT, uuid, 7)).orElseThrow();
            assertEquals(5, second.oldScore());
            assertEquals(12, second.newScore());

            var clamped = db.callSync(conn -> repo.addScore(conn, EVENT, uuid, -100)).orElseThrow();
            assertEquals(0, clamped.newScore(), "score clamps at zero");

            assertTrue(db.callSync(conn -> repo.addScore(conn, EVENT, UUID.randomUUID(), 5)).isEmpty(),
                    "non-participant yields no change");
        } finally {
            db.close();
        }
    }

    @Test
    void resumesInterruptedRewardDistribution() {
        events = build();
        EventRepository repo = new EventRepository();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try {
            db.runSync(conn -> {
                repo.join(conn, EVENT, a, 1L);
                repo.join(conn, EVENT, b, 1L);
            });
            // Simulate a crash: event marked COMPLETED with distribution pending, nothing granted yet.
            db.runSync(conn -> {
                repo.setState(conn, EVENT, "COMPLETED", null, 1L, 1L);
                repo.setRewardsDistributed(conn, EVENT, false);
            });
            assertEquals(0L, db.callSync(rewardRepo::queueCount), "nothing distributed before resume");

            assertEquals(1, events.resumePendingDistributions());
            assertEquals(2L, db.callSync(rewardRepo::queueCount), "both participants' rewards queued on resume");
            assertEquals(0, events.resumePendingDistributions(), "resume is a no-op once distributed");
        } finally {
            db.close();
        }
    }

    @Test
    void completedEventIsNotRedistributed() {
        events = build();
        UUID uuid = UUID.randomUUID();
        try {
            events.transition(EVENT, EventState.OPEN, "t");
            events.join(uuid, EVENT);
            events.transition(EVENT, EventState.ACTIVE, "t");
            events.transition(EVENT, EventState.COMPLETED, "t"); // distributes and marks done
            assertEquals(1L, db.callSync(rewardRepo::queueCount));
            assertEquals(0, events.resumePendingDistributions(), "a fully-distributed event is not resumed");
        } finally {
            db.close();
        }
    }
}
