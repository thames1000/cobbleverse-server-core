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
