package com.thamescape.cobbleverse.core.config;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates loaded configuration and returns a list of human-readable problems. An empty list means
 * the config is valid. The caller decides whether problems are fatal (they are, at startup).
 */
public final class ConfigValidator {

    private ConfigValidator() {
    }

    /** Validates {@link CoreConfig}, returning a list of problems (empty if valid). */
    public static List<String> validate(CoreConfig config) {
        List<String> problems = new ArrayList<>();

        if (config.configVersion <= 0) {
            problems.add("core.json: configVersion must be a positive integer (was "
                    + config.configVersion + ")");
        }
        if (config.configVersion > CoreConfig.CURRENT_VERSION) {
            problems.add("core.json: configVersion " + config.configVersion
                    + " is newer than this build supports (" + CoreConfig.CURRENT_VERSION
                    + "); upgrade the mod or fix the file");
        }
        if (isBlank(config.serverId)) {
            problems.add("core.json: serverId must not be blank");
        }
        if (isBlank(config.defaultLocale)) {
            problems.add("core.json: defaultLocale must not be blank");
        }
        if (!isValidTimezone(config.timezone)) {
            problems.add("core.json: timezone '" + config.timezone + "' is not a valid IANA zone id");
        }

        return problems;
    }

    /** Validates {@link DatabaseConfig}, returning a list of problems (empty if valid). */
    public static List<String> validate(DatabaseConfig config) {
        List<String> problems = new ArrayList<>();

        if (config.configVersion <= 0 || config.configVersion > DatabaseConfig.CURRENT_VERSION) {
            problems.add("database.json: configVersion " + config.configVersion + " is out of range");
        }
        if (!"sqlite".equalsIgnoreCase(config.type)) {
            problems.add("database.json: type '" + config.type
                    + "' is unsupported (only 'sqlite' is available in this version)");
        }
        if (isBlank(config.fileName)) {
            problems.add("database.json: fileName must not be blank");
        }
        if (config.flushIntervalSeconds <= 0) {
            problems.add("database.json: flushIntervalSeconds must be positive");
        }
        if (config.playtimeAccrualSeconds <= 0) {
            problems.add("database.json: playtimeAccrualSeconds must be positive");
        }

        return problems;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isValidTimezone(String zone) {
        if (isBlank(zone)) {
            return false;
        }
        try {
            ZoneId.of(zone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
