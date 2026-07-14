package com.thamescape.cobbleverse.core.reward;

import java.util.ArrayList;
import java.util.List;

/**
 * A named bundle of reward actions, loaded from {@code rewards.json}. {@link #id} is filled from the
 * map key after loading (it is not stored in the JSON body).
 */
public class RewardDefinition {

    /** Set from the definitions map key; not part of the JSON body. */
    public transient String id;

    public String displayName = "";

    /** If false (default), each player may claim this definition only once. */
    public boolean repeatable = false;

    public List<RewardEntry> rewards = new ArrayList<>();

    public String displayNameOrId() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }
}
