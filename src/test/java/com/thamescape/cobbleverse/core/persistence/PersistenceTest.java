package com.thamescape.cobbleverse.core.persistence;

import com.thamescape.cobbleverse.core.persistence.repository.PlayerProfileRepository;
import com.thamescape.cobbleverse.core.player.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end persistence test against a real SQLite database in a temp directory. Exercises
 * migrations, upsert semantics and lookups without the Minecraft classpath.
 */
class PersistenceTest {

    @TempDir
    Path tmp;

    private DatabaseManager open() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);
        return db;
    }

    @Test
    void migratesToLatestVersion() {
        DatabaseManager db = open();
        MigrationManager mig = MigrationManager.withDefaults();
        try {
            assertEquals(mig.latestVersion(), mig.currentVersion(db));
        } finally {
            db.close();
        }
    }

    @Test
    void migrationIsIdempotent() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager mig = MigrationManager.withDefaults();
        mig.migrate(db);
        mig.migrate(db); // second run must be a no-op
        try {
            assertEquals(mig.latestVersion(), mig.currentVersion(db));
        } finally {
            db.close();
        }
    }

    @Test
    void upsertInsertsThenUpdatesPreservingFirstJoin() {
        DatabaseManager db = open();
        PlayerProfileRepository repo = new PlayerProfileRepository();
        UUID id = UUID.randomUUID();
        try {
            PlayerProfile created = PlayerProfile.createNew(id, "Steve", 1000L);
            created.addPlaytimeSeconds(120);
            db.runSync(conn -> repo.upsert(conn, created));

            PlayerProfile loaded = db.callSync(conn -> repo.find(conn, id).orElseThrow());
            assertEquals("Steve", loaded.lastKnownName());
            assertEquals(1000L, loaded.firstJoinedAt());
            assertEquals(120L, loaded.playtimeSeconds());

            // Re-login: name and playtime change; first_joined_at must be preserved.
            loaded.setLastKnownName("Steve_v2");
            loaded.setLastJoinedAt(5000L);
            loaded.addPlaytimeSeconds(30);
            db.runSync(conn -> repo.upsert(conn, loaded));

            PlayerProfile again = db.callSync(conn -> repo.find(conn, id).orElseThrow());
            assertEquals("Steve_v2", again.lastKnownName());
            assertEquals(1000L, again.firstJoinedAt(), "first_joined_at must not change on update");
            assertEquals(5000L, again.lastJoinedAt());
            assertEquals(150L, again.playtimeSeconds());
            assertEquals(1L, db.callSync(repo::count), "update must not create a second row");
        } finally {
            db.close();
        }
    }

    @Test
    void findByNameIsCaseInsensitiveAndMissesUnknown() {
        DatabaseManager db = open();
        PlayerProfileRepository repo = new PlayerProfileRepository();
        UUID id = UUID.randomUUID();
        try {
            db.runSync(conn -> repo.upsert(conn, PlayerProfile.createNew(id, "Alex", 1L)));
            assertTrue(db.callSync(conn -> repo.findByName(conn, "alex")).isPresent());
            assertFalse(db.callSync(conn -> repo.findByName(conn, "nobody")).isPresent());
        } finally {
            db.close();
        }
    }

    @Test
    void dataSurvivesReopen() {
        UUID id = UUID.randomUUID();
        PlayerProfileRepository repo = new PlayerProfileRepository();

        DatabaseManager first = open();
        first.runSync(conn -> repo.upsert(conn, PlayerProfile.createNew(id, "Persist", 7L)));
        first.close();

        // Reopen the same file: the row and schema must still be there.
        DatabaseManager second = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        second.init();
        MigrationManager.withDefaults().migrate(second);
        try {
            assertEquals(MigrationManager.withDefaults().latestVersion(),
                    MigrationManager.withDefaults().currentVersion(second));
            PlayerProfile loaded = second.callSync(conn -> repo.find(conn, id).orElseThrow());
            assertEquals("Persist", loaded.lastKnownName());
        } finally {
            second.close();
        }
    }
}
