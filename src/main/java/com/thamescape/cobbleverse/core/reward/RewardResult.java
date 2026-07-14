package com.thamescape.cobbleverse.core.reward;

import java.util.List;

/**
 * Result of a reward operation. For a whole definition, {@link #lines} carries the per-entry detail;
 * for a single entry there is just the status and message.
 */
public record RewardResult(RewardStatus status, String message, List<String> lines) {

    public RewardResult(RewardStatus status, String message) {
        this(status, message, List.of());
    }

    public boolean ok() {
        return status == RewardStatus.SUCCESS;
    }

    public static RewardResult success(String message) {
        return new RewardResult(RewardStatus.SUCCESS, message);
    }

    public static RewardResult failed(String message) {
        return new RewardResult(RewardStatus.FAILED, message);
    }

    public static RewardResult unsupported(String message) {
        return new RewardResult(RewardStatus.UNSUPPORTED, message);
    }
}
