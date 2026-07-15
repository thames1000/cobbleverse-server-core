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

    /** Validates {@link RewardsConfig}, returning a list of problems (empty if valid). */
    public static List<String> validate(RewardsConfig config) {
        List<String> problems = new ArrayList<>();

        if (config.configVersion <= 0 || config.configVersion > RewardsConfig.CURRENT_VERSION) {
            problems.add("rewards.json: configVersion " + config.configVersion + " is out of range");
        }
        if (config.maxDeliveryAttempts <= 0) {
            problems.add("rewards.json: maxDeliveryAttempts must be positive");
        }
        if (config.definitions == null) {
            problems.add("rewards.json: definitions must be present");
            return problems;
        }

        for (var entry : config.definitions.entrySet()) {
            String id = entry.getKey();
            var def = entry.getValue();
            if (def == null || def.rewards == null || def.rewards.isEmpty()) {
                problems.add("rewards.json: definition '" + id + "' has no rewards");
                continue;
            }
            for (int i = 0; i < def.rewards.size(); i++) {
                var reward = def.rewards.get(i);
                String where = "rewards.json: definition '" + id + "' reward #" + (i + 1);
                var type = com.thamescape.cobbleverse.core.reward.RewardType.fromId(reward.typeOrEmpty());
                if (type.isEmpty()) {
                    problems.add(where + ": unknown type '" + reward.typeOrEmpty() + "'");
                    continue;
                }
                problems.addAll(validateRewardFields(where, type.get(), reward));
            }
        }
        return problems;
    }

    private static List<String> validateRewardFields(
            String where,
            com.thamescape.cobbleverse.core.reward.RewardType type,
            com.thamescape.cobbleverse.core.reward.RewardEntry reward) {
        List<String> problems = new ArrayList<>();
        switch (type) {
            case ITEM -> {
                if (isBlank(reward.item)) {
                    problems.add(where + ": item type requires 'item'");
                }
                if (reward.amount <= 0) {
                    problems.add(where + ": item amount must be positive");
                }
            }
            case CURRENCY -> {
                if (isBlank(reward.currency)) {
                    problems.add(where + ": currency type requires 'currency'");
                }
                if (reward.amount <= 0) {
                    problems.add(where + ": currency amount must be positive");
                }
            }
            case COMMAND -> {
                if (isBlank(reward.command)) {
                    problems.add(where + ": command type requires 'command'");
                }
            }
            case CRATE_KEY -> {
                if (isBlank(reward.key)) {
                    problems.add(where + ": crate_key type requires 'key'");
                }
            }
            case PERMISSION -> {
                if (isBlank(reward.node)) {
                    problems.add(where + ": permission type requires 'node'");
                }
            }
            case POKEMON, COSMETIC -> {
                if (isBlank(reward.value)) {
                    problems.add(where + ": " + type.id() + " type requires 'value'");
                }
            }
        }
        return problems;
    }

    /** Validates {@link SeasonsConfig}, returning a list of problems (empty if valid). */
    public static List<String> validate(SeasonsConfig config) {
        List<String> problems = new ArrayList<>();
        if (config.configVersion <= 0 || config.configVersion > SeasonsConfig.CURRENT_VERSION) {
            problems.add("seasons.json: configVersion " + config.configVersion + " is out of range");
        }
        if (config.seasons == null) {
            problems.add("seasons.json: seasons must be present");
            return problems;
        }
        for (var entry : config.seasons.entrySet()) {
            String id = entry.getKey();
            var season = entry.getValue();
            String where = "seasons.json: season '" + id + "'";
            boolean startValid = isValidDateTime(season.startsAt);
            boolean endValid = isValidDateTime(season.endsAt);
            if (!startValid) {
                problems.add(where + ": startsAt is not a valid ISO offset date-time");
            }
            if (!endValid) {
                problems.add(where + ": endsAt is not a valid ISO offset date-time");
            }
            if (startValid && endValid
                    && !java.time.OffsetDateTime.parse(season.endsAt)
                            .isAfter(java.time.OffsetDateTime.parse(season.startsAt))) {
                problems.add(where + ": endsAt must be after startsAt");
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (var objective : season.objectives) {
                if (isBlank(objective.id)) {
                    problems.add(where + ": an objective is missing 'id'");
                } else if (!seen.add(objective.id)) {
                    problems.add(where + ": duplicate objective id '" + objective.id + "'");
                }
                if (objective.required <= 0) {
                    problems.add(where + " objective '" + objective.id + "': required must be positive");
                }
                if (objective.points < 0) {
                    problems.add(where + " objective '" + objective.id + "': points must not be negative");
                }
                problems.addAll(validateObjectiveType(where + " objective '" + objective.id + "'", objective));
            }
            for (var milestone : season.milestones) {
                if (milestone.points < 0) {
                    problems.add(where + ": milestone points must not be negative");
                }
            }
        }
        return problems;
    }

    private static final java.util.Set<String> BATTLE_KINDS = java.util.Set.of("pvp", "pvn", "pvw");

    /** Validates a season objective's {@code type} and the matcher fields that type requires. */
    private static List<String> validateObjectiveType(
            String where, com.thamescape.cobbleverse.core.season.ObjectiveDefinition objective) {
        List<String> problems = new ArrayList<>();
        var type = com.thamescape.cobbleverse.core.season.objective.ObjectiveType.fromId(objective.type);
        if (type.isEmpty()) {
            problems.add(where + ": unknown objective type '" + objective.type + "'");
            return problems;
        }
        switch (type.get()) {
            case CAPTURE_SPECIES -> {
                if (isBlank(objective.species)) {
                    problems.add(where + ": capture_species requires a non-blank 'species'");
                }
            }
            case BATTLE_WON -> {
                if (objective.battleKind != null && !objective.battleKind.isBlank()
                        && !BATTLE_KINDS.contains(objective.battleKind.toLowerCase(java.util.Locale.ROOT))) {
                    problems.add(where + ": battleKind must be one of pvp/pvn/pvw (or blank), was '"
                            + objective.battleKind + "'");
                }
            }
            default -> {
                // manual / capture_shiny / capture_any need no matcher fields.
            }
        }
        return problems;
    }

    /** Validates {@link EventsConfig}, returning a list of problems (empty if valid). */
    public static List<String> validate(EventsConfig config) {
        List<String> problems = new ArrayList<>();
        if (config.configVersion <= 0 || config.configVersion > EventsConfig.CURRENT_VERSION) {
            problems.add("events.json: configVersion " + config.configVersion + " is out of range");
        }
        if (config.events == null) {
            problems.add("events.json: events must be present");
            return problems;
        }
        for (var entry : config.events.entrySet()) {
            String where = "events.json: event '" + entry.getKey() + "'";
            var event = entry.getValue();
            if (event.scheduledStart != null && !isValidDateTime(event.scheduledStart)) {
                problems.add(where + ": scheduledStart is not a valid ISO offset date-time");
            }
            if (event.scheduledEnd != null && !isValidDateTime(event.scheduledEnd)) {
                problems.add(where + ": scheduledEnd is not a valid ISO offset date-time");
            }
            if (event.rewards != null && event.rewards.stream().anyMatch(ConfigValidator::isBlank)) {
                problems.add(where + ": rewards contains a blank reward id");
            }
        }
        return problems;
    }

    private static boolean isValidDateTime(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            java.time.OffsetDateTime.parse(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cross-config integrity: season milestone rewards and event completion rewards must reference an
     * existing, <b>non-repeatable</b> reward definition. Non-repeatable is required because these
     * rewards are granted through replay-safe paths (season milestone re-crossing, event completion
     * resume) that rely on claim dedup — a repeatable reward could be granted more than once.
     */
    public static List<String> validateCrossReferences(RewardsConfig rewards, SeasonsConfig seasons,
                                                       EventsConfig events) {
        List<String> problems = new ArrayList<>();
        for (var season : seasons.seasons.entrySet()) {
            for (var milestone : season.getValue().milestones) {
                checkReplaySafeReward(rewards, milestone.reward,
                        "seasons.json: season '" + season.getKey() + "' milestone reward", problems);
            }
        }
        for (var event : events.events.entrySet()) {
            if (event.getValue().rewards != null) {
                for (String rewardId : event.getValue().rewards) {
                    checkReplaySafeReward(rewards, rewardId,
                            "events.json: event '" + event.getKey() + "' completion reward", problems);
                }
            }
        }
        return problems;
    }

    private static void checkReplaySafeReward(RewardsConfig rewards, String rewardId,
                                              String where, List<String> problems) {
        if (isBlank(rewardId)) {
            return; // absence handled by per-file validation
        }
        var def = rewards.definitions.get(rewardId);
        if (def == null) {
            problems.add(where + " '" + rewardId + "' does not exist in rewards.json");
        } else if (def.repeatable) {
            problems.add(where + " '" + rewardId + "' is repeatable; must be non-repeatable "
                    + "(replay-safe grants require claim dedup)");
        }
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
