package com.thamescape.cobbleverse.core.reward.type;

import com.thamescape.cobbleverse.core.reward.RewardContext;
import com.thamescape.cobbleverse.core.reward.RewardEntry;
import com.thamescape.cobbleverse.core.reward.RewardHandler;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import com.thamescape.cobbleverse.core.reward.RewardType;
import com.thamescape.cobbleverse.core.reward.currency.CurrencyService;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/** Deposits a currency amount via the {@link CurrencyService}. */
public final class CurrencyRewardHandler implements RewardHandler {

    private final CurrencyService currencies;

    public CurrencyRewardHandler(CurrencyService currencies) {
        this.currencies = currencies;
    }

    @Override
    public RewardType type() {
        return RewardType.CURRENCY;
    }

    @Override
    @Nullable
    public String validate(RewardEntry entry) {
        if (entry.currency == null || entry.currency.isBlank()) {
            return "currency reward is missing 'currency'";
        }
        if (entry.amount <= 0) {
            return "currency amount must be positive";
        }
        if (!currencies.isSupported(entry.currency)) {
            return "unknown currency '" + entry.currency + "'";
        }
        return null;
    }

    @Override
    public RewardResult execute(RewardEntry entry, RewardContext context) {
        boolean ok = currencies.deposit(entry.currency, context.uuid(),
                BigDecimal.valueOf(entry.amount), context.source());
        return ok ? RewardResult.success(describe(entry))
                : RewardResult.failed("currency deposit failed for '" + entry.currency + "'");
    }

    @Override
    public String describe(RewardEntry entry) {
        return entry.amount + " " + entry.currency;
    }
}
