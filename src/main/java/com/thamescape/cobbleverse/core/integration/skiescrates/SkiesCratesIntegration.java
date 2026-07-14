package com.thamescape.cobbleverse.core.integration.skiescrates;

import com.thamescape.cobbleverse.core.integration.AbstractModIntegration;

/**
 * Detects SkiesCrates. Key granting and crate-open tracking are added with the reward system
 * (0.3.0); the crate mod keeps ownership of animations, previews, holograms and reward weighting.
 */
public final class SkiesCratesIntegration extends AbstractModIntegration {

    public SkiesCratesIntegration() {
        super("skiescrates", "SkiesCrates", "skiescrates");
    }
}
