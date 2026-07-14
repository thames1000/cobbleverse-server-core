package com.thamescape.cobbleverse.core.reward;

import org.jetbrains.annotations.Nullable;

/**
 * One reward action within a {@link RewardDefinition}, as loaded from {@code rewards.json}. A single
 * heterogeneous DTO: each {@link RewardType} reads only the fields it needs, validated by its handler.
 */
public class RewardEntry {

    /** Reward type id, e.g. {@code item}, {@code currency}, {@code crate_key}. */
    public String type;

    // Common
    public long amount = 1;

    // item
    @Nullable
    public String item;

    // currency
    @Nullable
    public String currency;

    // command
    @Nullable
    public String command;

    // crate_key
    @Nullable
    public String key;

    // permission
    @Nullable
    public String node;

    // pokemon / cosmetic / generic value
    @Nullable
    public String value;

    public String typeOrEmpty() {
        return type == null ? "" : type;
    }
}
