package com.thamescape.cobbleverse.core.reward;

import org.jetbrains.annotations.Nullable;

/**
 * Executes one {@link RewardType}. Handlers are registered in a {@link RewardRegistry}, so adding a
 * reward type never means touching a giant switch.
 */
public interface RewardHandler {

    RewardType type();

    /** Structural validation of an entry's fields. Returns null if valid, else the problem. */
    @Nullable
    default String validate(RewardEntry entry) {
        return null;
    }

    /** Grants the reward to the (online) player in {@code context}. */
    RewardResult execute(RewardEntry entry, RewardContext context);

    /** One-line human description for previews, e.g. {@code "5x minecraft:diamond"}. */
    String describe(RewardEntry entry);
}
