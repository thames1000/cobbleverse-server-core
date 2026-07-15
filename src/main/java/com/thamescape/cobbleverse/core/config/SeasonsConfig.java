package com.thamescape.cobbleverse.core.config;

import com.thamescape.cobbleverse.core.season.Milestone;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;
import com.thamescape.cobbleverse.core.season.SeasonDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Season configuration, persisted as {@code config/cobbleverse-server-core/seasons.json}. Which season
 * is "current" is named by {@code core.json}'s {@code activeSeason}; this file holds the definitions.
 */
public class SeasonsConfig {

    public int configVersion = CURRENT_VERSION;

    public Map<String, SeasonDefinition> seasons = new LinkedHashMap<>();

    public static final int CURRENT_VERSION = 1;

    public static SeasonsConfig defaults() {
        SeasonsConfig config = new SeasonsConfig();

        SeasonDefinition sample = new SeasonDefinition();
        sample.displayName = "Sample Season";
        sample.startsAt = "2026-01-01T00:00:00Z";
        sample.endsAt = "2026-12-31T23:59:59Z";
        sample.enabled = false; // disabled by default so it doesn't auto-activate on a fresh install

        ObjectiveDefinition objective = new ObjectiveDefinition();
        objective.id = "sample_objective";
        objective.displayName = "Complete a sample objective";
        objective.type = "manual";
        objective.required = 5;
        objective.points = 20;
        sample.objectives.add(objective);

        Milestone milestone = new Milestone();
        milestone.points = 20;
        milestone.reward = "sample_tier_1";
        sample.milestones = List.of(milestone);

        config.seasons.put("sample_season", sample);
        return config;
    }
}
