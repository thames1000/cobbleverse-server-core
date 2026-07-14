package com.thamescape.cobbleverse.core.reward;

/** Outcome of granting a reward definition or executing a single reward entry. */
public enum RewardStatus {
    /** Everything granted successfully. */
    SUCCESS,
    /** Some entries succeeded, some failed. */
    PARTIAL,
    /** Nothing was granted. */
    FAILED,
    /** The player had already claimed this non-repeatable definition. */
    ALREADY_CLAIMED,
    /** The player was offline; the reward was queued for delivery on join. */
    QUEUED,
    /** The definition id does not exist. */
    UNKNOWN,
    /** The reward type is recognised but not usable (e.g. no command template configured). */
    UNSUPPORTED
}
