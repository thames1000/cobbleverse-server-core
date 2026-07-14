package com.thamescape.cobbleverse.core.reward.currency;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.audit.AuditService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes currency operations to the provider that owns each currency id, and audits every movement so
 * all currency changes flow through one central, logged path.
 */
public final class CurrencyService {

    private final ConcurrentHashMap<String, CurrencyProvider> providers = new ConcurrentHashMap<>();
    private final AuditService audit;

    public CurrencyService(AuditService audit) {
        this.audit = audit;
    }

    public void register(CurrencyProvider provider) {
        providers.put(provider.id(), provider);
    }

    public boolean isSupported(String currencyId) {
        return providers.containsKey(currencyId);
    }

    public Optional<CurrencyProvider> provider(String currencyId) {
        return Optional.ofNullable(providers.get(currencyId));
    }

    public Set<String> currencies() {
        return new TreeSet<>(providers.keySet());
    }

    /** Balance, or empty if the currency is unknown or the provider can't read balances. */
    public Optional<BigDecimal> balance(String currencyId, UUID player) {
        CurrencyProvider provider = providers.get(currencyId);
        if (provider == null || !provider.supportsBalance()) {
            return Optional.empty();
        }
        return Optional.of(provider.balance(player));
    }

    public boolean deposit(String currencyId, UUID player, BigDecimal amount, String reason) {
        CurrencyProvider provider = providers.get(currencyId);
        if (provider == null) {
            return false;
        }
        boolean ok = provider.deposit(player, amount);
        audit.record(auditEntry(AuditType.CURRENCY_DEPOSIT, player, currencyId, amount, reason, ok));
        return ok;
    }

    public boolean withdraw(String currencyId, UUID player, BigDecimal amount, String reason) {
        CurrencyProvider provider = providers.get(currencyId);
        if (provider == null) {
            return false;
        }
        boolean ok = provider.withdraw(player, amount);
        audit.record(auditEntry(AuditType.CURRENCY_WITHDRAW, player, currencyId, amount, reason, ok));
        return ok;
    }

    private AuditEntry.Builder auditEntry(AuditType type, UUID player, String currency,
                                          BigDecimal amount, String reason, boolean ok) {
        AuditEntry.Builder builder = AuditEntry.builder(type)
                .target(player)
                .source(reason)
                .context(amount.toPlainString() + " " + currency);
        if (!ok) {
            builder.failure("operation rejected (unsupported or insufficient funds)");
        }
        return builder;
    }
}
