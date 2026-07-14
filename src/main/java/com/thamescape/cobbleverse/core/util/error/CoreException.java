package com.thamescape.cobbleverse.core.util.error;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base class for all core-owned exceptions. Carries a stable error code plus optional context so
 * failures can be logged and surfaced consistently.
 *
 * <p>Error codes follow the {@code CV-<AREA>-<NNN>} convention, e.g. {@code CV-CONFIG-001}.
 */
public class CoreException extends RuntimeException {

    private final String errorCode;
    @Nullable
    private final UUID playerUuid;
    @Nullable
    private final String subjectId;
    private final boolean retrySafe;

    public CoreException(String errorCode, String message) {
        this(errorCode, message, null, null, null, false);
    }

    public CoreException(String errorCode, String message, @Nullable Throwable cause) {
        this(errorCode, message, cause, null, null, false);
    }

    public CoreException(String errorCode,
                         String message,
                         @Nullable Throwable cause,
                         @Nullable UUID playerUuid,
                         @Nullable String subjectId,
                         boolean retrySafe) {
        super(message, cause);
        this.errorCode = errorCode;
        this.playerUuid = playerUuid;
        this.subjectId = subjectId;
        this.retrySafe = retrySafe;
    }

    /** Stable machine-readable code, e.g. {@code CV-REWARD-004}. */
    public String errorCode() {
        return errorCode;
    }

    @Nullable
    public UUID playerUuid() {
        return playerUuid;
    }

    /** Relevant domain id (reward id, season id, event id, config key, ...). */
    @Nullable
    public String subjectId() {
        return subjectId;
    }

    /** Whether retrying the failed operation is safe (no partial side effects). */
    public boolean retrySafe() {
        return retrySafe;
    }

    @Override
    public String getMessage() {
        return "[" + errorCode + "] " + super.getMessage();
    }
}
