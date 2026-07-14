package com.thamescape.cobbleverse.core.integration.cobblemon;

import com.thamescape.cobbleverse.core.integration.AbstractModIntegration;

/**
 * Detects Cobblemon. Event listeners (capture, hatch, evolution, battle, raid) are wired up in later
 * versions once the objective system exists; 0.1.0 only reports presence.
 */
public final class CobblemonIntegration extends AbstractModIntegration {

    public CobblemonIntegration() {
        super("cobblemon", "Cobblemon", "cobblemon");
    }
}
