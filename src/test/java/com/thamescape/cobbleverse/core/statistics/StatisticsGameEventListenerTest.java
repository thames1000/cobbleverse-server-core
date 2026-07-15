package com.thamescape.cobbleverse.core.statistics;

import com.thamescape.cobbleverse.core.game.battle.BattleWonGameEvent;
import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.game.player.PlayerJoinedGameEvent;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.StatisticsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The statistics consumer reacting to game events. No Minecraft needed — game events are pure records.
 * Reads are queued after the async increments on the single DB worker thread, so they observe them.
 */
class StatisticsGameEventListenerTest {

    @TempDir
    Path tmp;

    @Test
    void gameEventsUpdateStatistics() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);
        StatisticsService stats = new StatisticsService(db, new StatisticsRepository());
        StatisticsGameEventListener listener = new StatisticsGameEventListener(stats);
        UUID uuid = UUID.randomUUID();
        try {
            listener.onGameEvent(new PokemonCapturedGameEvent(uuid, Instant.now(), "pikachu", false));
            listener.onGameEvent(new PokemonCapturedGameEvent(uuid, Instant.now(), "gyarados", true));
            listener.onGameEvent(new BattleWonGameEvent(uuid, Instant.now(), "pvp", "singles", false));
            listener.onGameEvent(new BattleWonGameEvent(uuid, Instant.now(), "pvw", "singles", true)); // wild
            listener.onGameEvent(new PlayerJoinedGameEvent(uuid, Instant.now(), "Steve"));

            assertEquals(2, stats.value(uuid, StatisticsService.CAPTURES));
            assertEquals(1, stats.value(uuid, StatisticsService.SHINIES));
            assertEquals(1, stats.value(uuid, StatisticsService.BATTLES_WON),
                    "wild-capture 'victories' must not count as battles won");
            assertEquals(1, stats.value(uuid, StatisticsService.SESSIONS));
        } finally {
            db.close();
        }
    }
}
