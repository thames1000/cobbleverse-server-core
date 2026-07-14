package com.thamescape.cobbleverse.core.integration.ledger;

import com.thamescape.cobbleverse.core.integration.AbstractModIntegration;

/**
 * Detects Ledger. Ledger owns block and inventory history; the core's own {@code AuditService}
 * covers server-specific actions and does not duplicate Ledger's data.
 */
public final class LedgerIntegration extends AbstractModIntegration {

    public LedgerIntegration() {
        super("ledger", "Ledger", "ledger");
    }
}
