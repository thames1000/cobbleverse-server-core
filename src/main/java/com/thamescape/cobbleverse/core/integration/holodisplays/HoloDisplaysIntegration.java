package com.thamescape.cobbleverse.core.integration.holodisplays;

import com.thamescape.cobbleverse.core.integration.AbstractModIntegration;

/**
 * Detects HoloDisplays. Later modules can drive holograms with core placeholders; 0.1.0 only reports
 * presence.
 */
public final class HoloDisplaysIntegration extends AbstractModIntegration {

    public HoloDisplaysIntegration() {
        super("holodisplays", "HoloDisplays", "holodisplays");
    }
}
