package com.thamescape.cobbleverse.core.integration.cobbledollars;

import com.thamescape.cobbleverse.core.integration.AbstractModIntegration;

/**
 * Detects CobbleDollars. It backs the {@code cobbledollars} currency provider once the currency
 * abstraction is added (0.3.0). The core never stores CobbleDollars balances itself.
 */
public final class CobbleDollarsIntegration extends AbstractModIntegration {

    public CobbleDollarsIntegration() {
        super("cobbledollars", "CobbleDollars", "cobbledollars");
    }
}
