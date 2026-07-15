package com.thamescape.cobbleverse.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ConfigValidator}. Pure logic, no Minecraft classpath required.
 */
class ConfigValidatorTest {

    @Test
    void defaultsAreValid() {
        assertTrue(ConfigValidator.validate(CoreConfig.defaults()).isEmpty(),
                "default config should validate cleanly");
    }

    @Test
    void blankServerIdIsRejected() {
        CoreConfig config = CoreConfig.defaults();
        config.serverId = "  ";
        List<String> problems = ConfigValidator.validate(config);
        assertFalse(problems.isEmpty(), "blank serverId must be flagged");
    }

    @Test
    void invalidTimezoneIsRejected() {
        CoreConfig config = CoreConfig.defaults();
        config.timezone = "Mars/Olympus_Mons";
        assertFalse(ConfigValidator.validate(config).isEmpty(), "invalid timezone must be flagged");
    }

    @Test
    void nonPositiveVersionIsRejected() {
        CoreConfig config = CoreConfig.defaults();
        config.configVersion = 0;
        assertFalse(ConfigValidator.validate(config).isEmpty(), "configVersion 0 must be flagged");
    }

    @Test
    void futureVersionIsRejected() {
        CoreConfig config = CoreConfig.defaults();
        config.configVersion = CoreConfig.CURRENT_VERSION + 1;
        assertFalse(ConfigValidator.validate(config).isEmpty(),
                "config from a newer build must be flagged");
    }

    @Test
    void databaseDefaultsAreValid() {
        assertTrue(ConfigValidator.validate(DatabaseConfig.defaults()).isEmpty(),
                "default database config should validate cleanly");
    }

    @Test
    void unsupportedDatabaseTypeIsRejected() {
        DatabaseConfig config = DatabaseConfig.defaults();
        config.type = "postgres";
        assertFalse(ConfigValidator.validate(config).isEmpty(),
                "only sqlite is supported in this version");
    }

    @Test
    void nonPositiveIntervalsAreRejected() {
        DatabaseConfig config = DatabaseConfig.defaults();
        config.flushIntervalSeconds = 0;
        assertFalse(ConfigValidator.validate(config).isEmpty(), "flush interval must be positive");
    }

    @Test
    void reversedSeasonDatesAreRejected() {
        SeasonsConfig config = SeasonsConfig.defaults();
        var season = config.seasons.get("sample_season");
        season.startsAt = "2026-12-31T00:00:00Z";
        season.endsAt = "2026-01-01T00:00:00Z";
        assertFalse(ConfigValidator.validate(config).isEmpty(), "endsAt before startsAt must be flagged");
    }

    @Test
    void unknownObjectiveTypeIsRejected() {
        SeasonsConfig config = SeasonsConfig.defaults();
        config.seasons.get("sample_season").objectives.get(0).type = "caputre_shiny"; // typo
        assertFalse(ConfigValidator.validate(config).isEmpty(), "a misspelled objective type must be flagged");
    }

    @Test
    void captureSpeciesWithoutSpeciesIsRejected() {
        SeasonsConfig config = SeasonsConfig.defaults();
        var objective = config.seasons.get("sample_season").objectives.get(0);
        objective.type = "capture_species";
        objective.species = "";
        assertFalse(ConfigValidator.validate(config).isEmpty(), "capture_species needs a species");
    }

    @Test
    void battleWonWithBadKindIsRejected() {
        SeasonsConfig config = SeasonsConfig.defaults();
        var objective = config.seasons.get("sample_season").objectives.get(0);
        objective.type = "battle_won";
        objective.battleKind = "trainer"; // not pvp/pvn/pvw
        assertFalse(ConfigValidator.validate(config).isEmpty(), "invalid battleKind must be flagged");
    }

    @Test
    void validEventDrivenObjectiveIsAccepted() {
        SeasonsConfig config = SeasonsConfig.defaults();
        var objective = config.seasons.get("sample_season").objectives.get(0);
        objective.type = "capture_species";
        objective.species = "pikachu";
        assertTrue(ConfigValidator.validate(config).isEmpty(), "a well-formed event objective is valid");
    }

    @Test
    void defaultsCrossValidateCleanly() {
        assertTrue(ConfigValidator.validateCrossReferences(
                RewardsConfig.defaults(), SeasonsConfig.defaults(), EventsConfig.defaults()).isEmpty());
    }

    @Test
    void repeatableMilestoneRewardIsRejected() {
        RewardsConfig rewards = RewardsConfig.defaults();
        rewards.definitions.get("sample_tier_1").repeatable = true; // milestone target must be non-repeatable
        List<String> problems = ConfigValidator.validateCrossReferences(
                rewards, SeasonsConfig.defaults(), EventsConfig.defaults());
        assertFalse(problems.isEmpty(), "a repeatable milestone/event reward must be flagged");
    }

    @Test
    void missingEventRewardIsRejected() {
        // Empty rewards config: the default event's completion reward can't be resolved.
        List<String> problems = ConfigValidator.validateCrossReferences(
                new RewardsConfig(), new SeasonsConfig(), EventsConfig.defaults());
        assertFalse(problems.isEmpty(), "an event reward that doesn't exist must be flagged");
    }
}
