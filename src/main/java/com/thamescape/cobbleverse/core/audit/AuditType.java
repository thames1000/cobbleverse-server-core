package com.thamescape.cobbleverse.core.audit;

/** Categories of auditable, core-owned actions. Block/inventory history stays in Ledger. */
public enum AuditType {
    ADMIN_COMMAND,
    REWARD_GRANTED,
    REWARD_CLAIMED,
    CURRENCY_DEPOSIT,
    CURRENCY_WITHDRAW,
    SEASON_CHANGED,
    EVENT_STARTED,
    EVENT_ENDED,
    PLAYER_PROFILE_EDITED,
    INTEGRATION_FAILURE,
    CONFIG_RELOAD
}
