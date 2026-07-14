package com.thamescape.cobbleverse.core.integration.placeholder;

import com.thamescape.cobbleverse.core.integration.AbstractModIntegration;

/**
 * Detects Text Placeholder API (Fabric). Custom {@code %cobbleverse_*%} placeholders are registered
 * with the profile/season systems in later versions; 0.1.0 only reports presence.
 */
public final class PlaceholderApiIntegration extends AbstractModIntegration {

    public PlaceholderApiIntegration() {
        super("placeholder-api", "PlaceholderAPI", "placeholder-api");
    }
}
