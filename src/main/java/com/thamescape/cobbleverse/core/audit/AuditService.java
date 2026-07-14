package com.thamescape.cobbleverse.core.audit;

import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.repository.AuditRepository;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records core-owned actions. Logs to the {@code AUDIT} logger and retains a bounded in-memory buffer
 * of recent entries for diagnostics. When a database is wired in (0.2.0+), entries are also persisted
 * asynchronously to the {@code audit_log} table.
 */
public final class AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/AUDIT");
    private static final int MAX_RECENT = 200;

    private final boolean enabled;
    @Nullable
    private final DatabaseManager db;
    @Nullable
    private final AuditRepository repository;
    private final Deque<AuditEntry> recent = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();

    /** In-memory only (no persistence). */
    public AuditService(boolean enabled) {
        this(enabled, null, null);
    }

    /** Persists to {@code audit_log} when {@code db} and {@code repository} are non-null. */
    public AuditService(boolean enabled, @Nullable DatabaseManager db, @Nullable AuditRepository repository) {
        this.enabled = enabled;
        this.db = db;
        this.repository = repository;
    }

    /** Records an entry. The current time is stamped here so callers stay simple. */
    public void record(AuditEntry.Builder builder) {
        if (!enabled) {
            return;
        }
        AuditEntry entry = builder.build(Instant.now());
        recent.addLast(entry);
        if (size.incrementAndGet() > MAX_RECENT) {
            recent.pollFirst();
            size.decrementAndGet();
        }
        log(entry);
        persist(entry);
    }

    private void persist(AuditEntry entry) {
        if (db == null || repository == null) {
            return;
        }
        db.runAsync(conn -> repository.insert(conn, entry))
                .exceptionally(t -> {
                    LOGGER.warn("Failed to persist audit entry {}: {}", entry.action(), t.getMessage());
                    return null;
                });
    }

    private void log(AuditEntry e) {
        String actor = e.actorName() != null ? e.actorName()
                : (e.actor() != null ? e.actor().toString() : "console");
        String result = e.success() ? "SUCCESS" : "FAILURE(" + e.failureReason() + ")";
        LOGGER.info("{} actor={} target={} source={} context={} result={}",
                e.action(), actor, e.target(), e.source(), e.context(), result);
    }

    /** Snapshot of recent entries, oldest first. */
    public List<AuditEntry> recent() {
        return new ArrayList<>(recent);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean persistent() {
        return db != null && repository != null;
    }

    /** Total persisted audit rows, or -1 if persistence is not configured. */
    public long storedCount() {
        if (db == null || repository == null) {
            return -1L;
        }
        return db.callSync(repository::count);
    }
}
