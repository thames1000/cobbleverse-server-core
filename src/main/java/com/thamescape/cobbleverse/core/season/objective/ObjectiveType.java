package com.thamescape.cobbleverse.core.season.objective;

import java.util.Locale;
import java.util.Optional;

/**
 * The objective types the core recognises. Behaviour is dispatched by the {@link ObjectiveRegistry}
 * (data-driven, no central switch), but this enum is the source of truth for <b>config validation</b>
 * — so an unknown or misspelled objective type fails loudly at load rather than silently never
 * matching. {@link #MANUAL} is admin/module-driven; the rest are game-event-driven.
 */
public enum ObjectiveType {
    MANUAL,
    CAPTURE_SPECIES,
    CAPTURE_SHINY,
    CAPTURE_ANY,
    BATTLE_WON;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ObjectiveType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ObjectiveType type : values()) {
            if (type.id().equals(id.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
