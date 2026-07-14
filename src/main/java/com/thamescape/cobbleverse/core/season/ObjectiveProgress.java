package com.thamescape.cobbleverse.core.season;

/** A player's progress on one objective. */
public record ObjectiveProgress(String objectiveId, int progress, boolean completed) {
}
