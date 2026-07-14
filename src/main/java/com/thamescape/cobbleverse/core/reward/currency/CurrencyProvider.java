package com.thamescape.cobbleverse.core.reward.currency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single currency, abstracted so the core doesn't care whether it's stored internally or owned by
 * another mod (CobbleDollars, ...). One provider handles one currency {@link #id()}.
 */
public interface CurrencyProvider {

    /** The currency id this provider handles, e.g. {@code event_tokens} or {@code cobbledollars}. */
    String id();

    /** Current balance, or {@link BigDecimal#ZERO} if unknown / unsupported (see {@link #supportsBalance}). */
    BigDecimal balance(UUID player);

    /** Adds {@code amount} to the player's balance. Returns true on success. */
    boolean deposit(UUID player, BigDecimal amount);

    /** Removes {@code amount} if the player can afford it. Returns true on success. */
    boolean withdraw(UUID player, BigDecimal amount);

    /** Whether {@link #balance} returns a meaningful value (command-backed providers cannot read it). */
    default boolean supportsBalance() {
        return true;
    }
}
