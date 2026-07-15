package com.thamescape.cobbleverse.core.config;

/**
 * An immutable, self-consistent generation of all configuration. Held by a single {@code volatile}
 * reference in {@link ConfigManager} and replaced in one assignment, so a reader always sees one
 * coherent generation — never a mix of new and old files across a reload.
 */
public record ConfigSnapshot(
        CoreConfig core,
        DatabaseConfig database,
        RewardsConfig rewards,
        SeasonsConfig seasons,
        EventsConfig events) {
}
