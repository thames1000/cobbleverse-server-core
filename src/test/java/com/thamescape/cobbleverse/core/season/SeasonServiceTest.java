package com.thamescape.cobbleverse.core.season;

import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigLoader;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.RewardRepository;
import com.thamescape.cobbleverse.core.persistence.repository.SeasonRepository;
import com.thamescape.cobbleverse.core.reward.RewardRegistry;
import com.thamescape.cobbleverse.core.reward.RewardService;
import com.thamescape.cobbleverse.core.season.objective.ManualObjectiveHandler;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the season lifecycle that 0.4.0 must prove: objective progress → completion → points →
 * milestone reward (queued because there's no live server in a unit test), plus state derivation.
 */
class SeasonServiceTest {

    @TempDir
    Path tmp;

    private static String iso(int daysFromNow) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysFromNow)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private DatabaseManager openDb() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);
        return db;
    }

    private ConfigManager config(String activeSeason, String seasonsJson) throws IOException {
        Path cfg = tmp.resolve("config");
        Files.createDirectories(cfg);
        Files.writeString(cfg.resolve("core.json"),
                "{\"activeSeason\":\"" + activeSeason + "\"}", StandardCharsets.UTF_8);
        Files.writeString(cfg.resolve("seasons.json"), seasonsJson, StandardCharsets.UTF_8);
        ConfigManager manager = new ConfigManager(new ConfigLoader(cfg));
        manager.load();
        return manager;
    }

    private SeasonService seasonService(ConfigManager config, DatabaseManager db) {
        AuditService audit = new AuditService(true);
        RewardService rewards = new RewardService(config, db, new RewardRepository(),
                new RewardRegistry(), audit);
        ObjectiveRegistry objectives = new ObjectiveRegistry();
        objectives.register(new ManualObjectiveHandler());
        return new SeasonService(config, db, new SeasonRepository(), rewards, audit, objectives);
    }

    private String seasonJson(int startDays, int endDays) {
        // objective needs 3 progress for 10 points; a milestone at 10 grants sample_tier_1.
        return "{\"configVersion\":1,\"seasons\":{\"test_season\":{"
                + "\"displayName\":\"Test\",\"enabled\":true,"
                + "\"startsAt\":\"" + iso(startDays) + "\",\"endsAt\":\"" + iso(endDays) + "\","
                + "\"objectives\":[{\"id\":\"obj1\",\"type\":\"manual\",\"required\":3,\"points\":10}],"
                + "\"milestones\":[{\"points\":10,\"reward\":\"sample_tier_1\"}]}}}";
    }

    private String activeSeasonJson() {
        return seasonJson(-1, 1);
    }

    @Test
    void objectiveCompletionAwardsPointsAndQueuesMilestoneReward() throws IOException {
        DatabaseManager db = openDb();
        SeasonService seasons = seasonService(config("test_season", activeSeasonJson()), db);
        RewardRepository rewardRepo = new RewardRepository();
        UUID uuid = UUID.randomUUID();
        try {
            var progress = seasons.addObjectiveProgress(uuid, "test_season", "obj1", 3);
            assertEquals(SeasonService.ObjectiveResult.Status.COMPLETED, progress.status());
            assertEquals(10, seasons.points(uuid, "test_season"), "completing the objective awards points");
            // Crossing the 10-point milestone grants sample_tier_1; offline, so it queues.
            assertEquals(1L, db.callSync(rewardRepo::queueCount), "milestone reward should be queued");
        } finally {
            db.close();
        }
    }

    @Test
    void partialProgressThenCompletion() throws IOException {
        DatabaseManager db = openDb();
        SeasonService seasons = seasonService(config("test_season", activeSeasonJson()), db);
        UUID uuid = UUID.randomUUID();
        try {
            var first = seasons.addObjectiveProgress(uuid, "test_season", "obj1", 2);
            assertEquals(SeasonService.ObjectiveResult.Status.PROGRESSED, first.status());
            assertEquals(0, seasons.points(uuid, "test_season"));

            var second = seasons.addObjectiveProgress(uuid, "test_season", "obj1", 5);
            assertEquals(SeasonService.ObjectiveResult.Status.COMPLETED, second.status());
            assertEquals(3, second.progress(), "progress is capped at required");
            assertEquals(10, seasons.points(uuid, "test_season"));

            // A further attempt is a no-op.
            var third = seasons.addObjectiveProgress(uuid, "test_season", "obj1", 5);
            assertEquals(SeasonService.ObjectiveResult.Status.ALREADY_COMPLETE, third.status());
            assertEquals(10, seasons.points(uuid, "test_season"), "points unchanged after completion");
        } finally {
            db.close();
        }
    }

    @Test
    void objectiveProgressRequiresActiveSeason() throws IOException {
        DatabaseManager db = openDb();
        // Season is enabled but its window is in the past -> ENDED, not ACTIVE.
        SeasonService seasons = seasonService(config("test_season", seasonJson(-2, -1)), db);
        UUID uuid = UUID.randomUUID();
        try {
            var result = seasons.addObjectiveProgress(uuid, "test_season", "obj1", 3);
            assertEquals(SeasonService.ObjectiveResult.Status.SEASON_NOT_ACTIVE, result.status());
            assertEquals(0, seasons.points(uuid, "test_season"), "no points awarded outside an active season");
        } finally {
            db.close();
        }
    }

    @Test
    void objectiveProgressClampsAtZero() throws IOException {
        DatabaseManager db = openDb();
        SeasonService seasons = seasonService(config("test_season", activeSeasonJson()), db);
        UUID uuid = UUID.randomUUID();
        try {
            assertEquals(2, seasons.addObjectiveProgress(uuid, "test_season", "obj1", 2).progress());
            // A negative correction cannot drive progress below zero.
            assertEquals(0, seasons.addObjectiveProgress(uuid, "test_season", "obj1", -5).progress());
        } finally {
            db.close();
        }
    }

    @Test
    void pointsClampAtZero() throws IOException {
        DatabaseManager db = openDb();
        SeasonService seasons = seasonService(config("test_season", activeSeasonJson()), db);
        UUID uuid = UUID.randomUUID();
        try {
            seasons.addPoints(uuid, "test_season", 5, "test");
            int total = seasons.addPoints(uuid, "test_season", -20, "test");
            assertEquals(0, total, "points must not go negative");
        } finally {
            db.close();
        }
    }

    @Test
    void stateDerivation() throws IOException {
        String json = "{\"configVersion\":1,\"seasons\":{"
                + "\"active\":{\"enabled\":true,\"startsAt\":\"" + iso(-1) + "\",\"endsAt\":\"" + iso(1) + "\"},"
                + "\"upcoming\":{\"enabled\":true,\"startsAt\":\"" + iso(1) + "\",\"endsAt\":\"" + iso(2) + "\"},"
                + "\"ended\":{\"enabled\":true,\"startsAt\":\"" + iso(-2) + "\",\"endsAt\":\"" + iso(-1) + "\"},"
                + "\"off\":{\"enabled\":false,\"startsAt\":\"" + iso(-1) + "\",\"endsAt\":\"" + iso(1) + "\"}}}";
        DatabaseManager db = openDb();
        SeasonService seasons = seasonService(config("active", json), db);
        try {
            assertEquals(SeasonState.ACTIVE, seasons.state(seasons.definition("active").orElseThrow()));
            assertEquals(SeasonState.UPCOMING, seasons.state(seasons.definition("upcoming").orElseThrow()));
            assertEquals(SeasonState.ENDED, seasons.state(seasons.definition("ended").orElseThrow()));
            assertEquals(SeasonState.DISABLED, seasons.state(seasons.definition("off").orElseThrow()));
        } finally {
            db.close();
        }
    }
}
