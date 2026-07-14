package com.thamescape.cobbleverse.core.audit;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A single audit record. Immutable; build via {@link #builder(AuditType)}.
 *
 * <p>In 0.1.0 entries are logged and kept in a bounded in-memory buffer. From 0.3.0 they are also
 * persisted to the {@code audit_log} table.
 */
public record AuditEntry(
        Instant timestamp,
        AuditType action,
        @Nullable UUID actor,
        @Nullable String actorName,
        @Nullable UUID target,
        String source,
        @Nullable String context,
        @Nullable String before,
        @Nullable String after,
        boolean success,
        @Nullable String failureReason
) {

    public static Builder builder(AuditType action) {
        return new Builder(action);
    }

    public static final class Builder {
        private final AuditType action;
        private UUID actor;
        private String actorName;
        private UUID target;
        private String source = "core";
        private String context;
        private String before;
        private String after;
        private boolean success = true;
        private String failureReason;

        private Builder(AuditType action) {
            this.action = action;
        }

        public Builder actor(@Nullable UUID actor, @Nullable String actorName) {
            this.actor = actor;
            this.actorName = actorName;
            return this;
        }

        public Builder target(@Nullable UUID target) {
            this.target = target;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder context(@Nullable String context) {
            this.context = context;
            return this;
        }

        public Builder change(@Nullable String before, @Nullable String after) {
            this.before = before;
            this.after = after;
            return this;
        }

        public Builder failure(String reason) {
            this.success = false;
            this.failureReason = reason;
            return this;
        }

        public AuditEntry build(Instant now) {
            return new AuditEntry(now, action, actor, actorName, target, source,
                    context, before, after, success, failureReason);
        }
    }
}
