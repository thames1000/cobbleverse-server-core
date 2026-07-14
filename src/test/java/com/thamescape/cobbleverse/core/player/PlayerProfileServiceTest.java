package com.thamescape.cobbleverse.core.player;

import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.PlayerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link PlayerProfileService#createIfAbsent} — the logic behind {@code /cvcore player create}.
 * No Minecraft classpath required; the service only touches the database and cache.
 */
class PlayerProfileServiceTest {

    @TempDir
    Path tmp;

    private DatabaseManager open() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);
        return db;
    }

    @Test
    void createsOnceThenReportsExisting() {
        DatabaseManager db = open();
        PlayerProfileService service = new PlayerProfileService(db, new PlayerProfileRepository());
        UUID uuid = UUID.randomUUID();
        try {
            assertEquals(PlayerProfileService.ProfileCreation.CREATED,
                    service.createIfAbsent(uuid, "Preseed", 1_000L));
            assertEquals(PlayerProfileService.ProfileCreation.ALREADY_EXISTS,
                    service.createIfAbsent(uuid, "Preseed", 2_000L));

            // The stored profile keeps its original data; the second call must not overwrite it.
            var stored = service.find(uuid).orElseThrow();
            assertEquals("Preseed", stored.lastKnownName());
            assertEquals(1_000L, stored.firstJoinedAt());
            assertEquals(1L, service.storedCount());
        } finally {
            db.close();
        }
    }
}
