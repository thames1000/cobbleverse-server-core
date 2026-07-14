package com.thamescape.cobbleverse.core.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records core-owned actions. In 0.1.0 this logs to the {@code AUDIT} logger and retains a bounded
 * in-memory buffer of recent entries for diagnostics. Persistence to the {@code audit_log} table
 * lands with the database layer (0.3.0).
 */
public final class AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/AUDIT");
    private static final int MAX_RECENT = 200;

    private final boolean enabled;
    private final Deque<AuditEntry> recent = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();

    public AuditService(boolean enabled) {
        this.enabled = enabled;
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
}
