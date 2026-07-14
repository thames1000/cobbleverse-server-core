package com.thamescape.cobbleverse.core.season;

import java.util.Map;

/** A player's progress in a season: total points and per-objective progress. */
public record SeasonProgress(String seasonId, int points, Map<String, ObjectiveProgress> objectives) {
}
