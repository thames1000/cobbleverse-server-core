package com.thamescape.cobbleverse.core.config;

import java.util.List;

/**
 * A semantic validator that judges a whole candidate {@link ConfigSnapshot} against live runtime state
 * that structural validation cannot see — for example, whether every configured objective type has a
 * registered handler. Registered with {@link ConfigManager#addSemanticValidator(SnapshotValidator)},
 * it runs at startup and again on every {@link ConfigManager#reload()}, before the candidate generation
 * is published — so a reload can never publish config that a subsystem considers semantically broken.
 */
@FunctionalInterface
public interface SnapshotValidator {

    /** Returns validation problems for the candidate generation; empty if it is acceptable. */
    List<String> validate(ConfigSnapshot candidate);
}
