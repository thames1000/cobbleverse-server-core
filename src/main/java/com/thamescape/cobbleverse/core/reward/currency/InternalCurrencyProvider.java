package com.thamescape.cobbleverse.core.reward.currency;

import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.TransactionManager;
import com.thamescape.cobbleverse.core.persistence.repository.CurrencyRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A core-owned currency stored in the database. Balance changes run in a transaction that also writes
 * a ledger row, so a balance and its history never diverge.
 *
 * <p>Operations run synchronously on the database worker thread; currency changes are infrequent
 * (reward grants, purchases) and must be correct, so blocking the caller briefly is acceptable.
 */
public final class InternalCurrencyProvider implements CurrencyProvider {

    private final String currencyId;
    private final DatabaseManager db;
    private final CurrencyRepository repository;

    public InternalCurrencyProvider(String currencyId, DatabaseManager db, CurrencyRepository repository) {
        this.currencyId = currencyId;
        this.db = db;
        this.repository = repository;
    }

    @Override
    public String id() {
        return currencyId;
    }

    @Override
    public BigDecimal balance(UUID player) {
        return db.callSync(conn -> repository.balance(conn, player, currencyId));
    }

    @Override
    public boolean deposit(UUID player, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return false;
        }
        db.runSync(conn -> TransactionManager.execute(conn, c -> {
            BigDecimal updated = repository.balance(c, player, currencyId).add(amount);
            repository.setBalance(c, player, currencyId, updated);
            repository.recordTransaction(c, player, currencyId, amount, "DEPOSIT",
                    System.currentTimeMillis(), "deposit");
        }));
        return true;
    }

    @Override
    public boolean withdraw(UUID player, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return false;
        }
        return db.callSync(conn -> {
            boolean[] ok = {false};
            TransactionManager.execute(conn, c -> {
                BigDecimal current = repository.balance(c, player, currencyId);
                if (current.compareTo(amount) < 0) {
                    return; // insufficient funds; leave ok=false
                }
                repository.setBalance(c, player, currencyId, current.subtract(amount));
                repository.recordTransaction(c, player, currencyId, amount, "WITHDRAW",
                        System.currentTimeMillis(), "withdraw");
                ok[0] = true;
            });
            return ok[0];
        });
    }
}
