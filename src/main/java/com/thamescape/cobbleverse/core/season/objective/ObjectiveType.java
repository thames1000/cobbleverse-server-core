package com.thamescape.cobbleverse.core.season.objective;

import java.util.Locale;
import java.util.Optional;

/**
 * The <b>built-in</b> objective types the core ships. Behaviour is dispatched by the
 * {@link ObjectiveRegistry} (data-driven, no central switch); the registry — not this enum — is the
 * authority for which types exist, so modules can register their own. This enum only drives built-in
 * matcher-field validation (e.g. {@link #CAPTURE_SPECIES} requires a species); type existence is
 * confirmed against the live registry at startup. {@link #MANUAL} is admin/module-driven; the rest are
 * game-event-driven.
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
