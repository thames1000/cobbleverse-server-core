package com.thamescape.cobbleverse.core.season.objective;

import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigLoader;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.RewardRepository;
import com.thamescape.cobbleverse.core.persistence.repository.SeasonRepository;
import com.thamescape.cobbleverse.core.reward.RewardRegistry;
import com.thamescape.cobbleverse.core.reward.RewardService;
import com.thamescape.cobbleverse.core.season.SeasonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: a capture game event drives season objective progress through the listener — the whole
 * bus → season consumer path, exercised with a synthetic event (no Cobblemon needed).
 */
class SeasonObjectiveEventListenerTest {

    @TempDir
    Path tmp;

    private DatabaseManager db;
    private SeasonService seasons;

    private static String iso(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Active season with one capture_species=pikachu objective (required 2, points 10). */
    private SeasonObjectiveEventListener build() throws IOException {
        Path cfg = tmp.resolve("config");
        Files.createDirectories(cfg);
        Files.writeString(cfg.resolve("core.json"),
                "{\"activeSeason\":\"test_season\"}", StandardCharsets.UTF_8);
        Files.writeString(cfg.resolve("seasons.json"), "{\"configVersion\":1,\"seasons\":{\"test_season\":{"
                + "\"displayName\":\"Test\",\"enabled\":true,"
                + "\"startsAt\":\"" + iso(-1) + "\",\"endsAt\":\"" + iso(1) + "\","
                + "\"objectives\":[{\"id\":\"catch_pika\",\"type\":\"capture_species\","
                + "\"species\":\"pikachu\",\"required\":2,\"points\":10}]}}}", StandardCharsets.UTF_8);

        ConfigManager config = new ConfigManager(new ConfigLoader(cfg));
        config.load();
        db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);

        AuditService audit = new AuditService(true);
        RewardService rewards = new RewardService(config, db, new RewardRepository(), new RewardRegistry(), audit);
        ObjectiveRegistry registry = new ObjectiveRegistry();
        registry.register(new ManualObjectiveHandler());
        registry.register(new CaptureSpeciesObjectiveHandler());
        seasons = new SeasonService(config, db, new SeasonRepository(), rewards, audit, registry);
        return new SeasonObjectiveEventListener(seasons);
    }

    private static PokemonCapturedGameEvent capture(UUID uuid, String species) {
        return new PokemonCapturedGameEvent(uuid, Instant.now(), species, false);
    }

    @Test
    void captureEventAdvancesMatchingObjective() throws IOException {
        SeasonObjectiveEventListener listener = build();
        UUID uuid = UUID.randomUUID();
        try {
            listener.onGameEvent(capture(uuid, "pikachu"));
            assertEquals(1, seasons.progress(uuid, "test_season").objectives().get("catch_pika").progress());
            assertEquals(0, seasons.points(uuid, "test_season"), "not complete yet");

            listener.onGameEvent(capture(uuid, "bulbasaur")); // no match
            assertEquals(1, seasons.progress(uuid, "test_season").objectives().get("catch_pika").progress());

            listener.onGameEvent(capture(uuid, "pikachu")); // completes -> awards points
            assertEquals(10, seasons.points(uuid, "test_season"), "completing the objective awards points");
        } finally {
            db.close();
        }
    }

    @Test
    void publishingDoesNotBlockOnTheDatabase() throws Exception {
        SeasonObjectiveEventListener listener = build();
        UUID uuid = UUID.randomUUID();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            // Occupy the single database worker with a task that blocks until released.
            db.runAsync(conn -> {
                entered.countDown();
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            // Wait until the worker has actually picked up the blocking task, so the timing below
            // genuinely measures onGameEvent against a busy worker rather than an idle one.
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "database worker did not start the blocking task");

            long start = System.nanoTime();
            listener.onGameEvent(capture(uuid, "pikachu")); // must only compute matches + enqueue
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            gate.countDown();

            assertTrue(elapsedMs < 1000,
                    "onGameEvent must not block on the database worker (took " + elapsedMs + "ms)");
            // Once the worker drains, the progress is applied.
            assertEquals(1, seasons.progress(uuid, "test_season").objectives().get("catch_pika").progress());
        } finally {
            db.close();
        }
    }
}
