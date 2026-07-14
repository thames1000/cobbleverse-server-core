package com.thamescape.cobbleverse.core.reward;

import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.persistence.SqliteDatabaseProvider;
import com.thamescape.cobbleverse.core.persistence.repository.CurrencyRepository;
import com.thamescape.cobbleverse.core.reward.currency.CurrencyService;
import com.thamescape.cobbleverse.core.reward.currency.InternalCurrencyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the internal (DB-backed) currency provider and the routing/audit service. */
class CurrencyTest {

    @TempDir
    Path tmp;

    private DatabaseManager open() {
        DatabaseManager db = new DatabaseManager(new SqliteDatabaseProvider(tmp.resolve("core.db")));
        db.init();
        MigrationManager.withDefaults().migrate(db);
        return db;
    }

    @Test
    void depositWithdrawAndBalance() {
        DatabaseManager db = open();
        CurrencyService currencies = new CurrencyService(new AuditService(true));
        currencies.register(new InternalCurrencyProvider("event_tokens", db, new CurrencyRepository()));
        UUID uuid = UUID.randomUUID();
        try {
            assertEquals(BigDecimal.ZERO, currencies.balance("event_tokens", uuid).orElseThrow());

            assertTrue(currencies.deposit("event_tokens", uuid, BigDecimal.valueOf(500), "test"));
            assertEquals(0, currencies.balance("event_tokens", uuid).orElseThrow()
                    .compareTo(BigDecimal.valueOf(500)));

            assertTrue(currencies.withdraw("event_tokens", uuid, BigDecimal.valueOf(200), "test"));
            assertEquals(0, currencies.balance("event_tokens", uuid).orElseThrow()
                    .compareTo(BigDecimal.valueOf(300)));

            // Overdraw is rejected and leaves the balance untouched.
            assertFalse(currencies.withdraw("event_tokens", uuid, BigDecimal.valueOf(1000), "test"));
            assertEquals(0, currencies.balance("event_tokens", uuid).orElseThrow()
                    .compareTo(BigDecimal.valueOf(300)));
        } finally {
            db.close();
        }
    }

    @Test
    void unknownCurrencyIsRejected() {
        DatabaseManager db = open();
        CurrencyService currencies = new CurrencyService(new AuditService(true));
        try {
            assertFalse(currencies.isSupported("nope"));
            assertFalse(currencies.deposit("nope", UUID.randomUUID(), BigDecimal.TEN, "test"));
            assertTrue(currencies.balance("nope", UUID.randomUUID()).isEmpty());
        } finally {
            db.close();
        }
    }
}
