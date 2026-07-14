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
}
