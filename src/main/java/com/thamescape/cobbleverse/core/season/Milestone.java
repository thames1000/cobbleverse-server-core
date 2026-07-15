package com.thamescape.cobbleverse.core.season;

/**
 * A season points milestone: reaching {@link #points} grants the reward definition {@link #reward}
 * through the central reward service (so it inherits claim dedup and offline queueing).
 */
public class Milestone {

    public int points;
    public String reward;
}
