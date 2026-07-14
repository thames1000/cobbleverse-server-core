package com.thamescape.cobbleverse.core.reward;

import java.util.Locale;
import java.util.Optional;

/** The kinds of reward the core knows how to grant. */
public enum RewardType {
    /** A vanilla / mod item given to the player's inventory. Native. */
    ITEM,
    /** A server command executed with placeholders. Native. */
    COMMAND,
    /** A currency deposit via a {@link com.thamescape.cobbleverse.core.reward.currency.CurrencyProvider}. */
    CURRENCY,
    /** A crate key, delivered via a configurable command template (e.g. SkiesCrates). */
    CRATE_KEY,
    /** A permission grant, delivered via a configurable command template (e.g. LuckPerms). */
    PERMISSION,
    /** A Pokémon, delivered via a configurable command template (e.g. Cobblemon). */
    POKEMON,
    /** A cosmetic unlock, delivered via a configurable command template. */
    COSMETIC;

    /** Parses a config type string (case-insensitive), if recognised. */
    public static Optional<RewardType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(RewardType.valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
