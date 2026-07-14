package com.thamescape.cobbleverse.core.config;

import com.thamescape.cobbleverse.core.reward.RewardDefinition;
import com.thamescape.cobbleverse.core.reward.RewardEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reward and currency configuration, persisted as {@code config/cobbleverse-server-core/rewards.json}.
 *
 * <p>{@link #definitions} maps a reward id to its bundle of actions. {@link #internalCurrencies} lists
 * the currency ids the core stores itself (DB-backed). {@link #templates} supplies command templates
 * for reward types that delegate to external mods (crate keys, permissions, Pokémon, cosmetics,
 * CobbleDollars).
 */
public class RewardsConfig {

    public int configVersion = CURRENT_VERSION;

    /** Currency ids the core owns and stores in its own database. */
    public List<String> internalCurrencies = new ArrayList<>(
            List.of("event_tokens", "battle_points", "cosmetic_shards"));

    /** Failed queued deliveries dead-letter after this many attempts (then need an admin retry). */
    public int maxDeliveryAttempts = 5;

    public Templates templates = new Templates();

    public Map<String, RewardDefinition> definitions = new LinkedHashMap<>();

    public static final int CURRENT_VERSION = 1;

    /**
     * Command templates for reward types that delegate to another mod. Blank means "unsupported"
     * (the reward reports a clear failure rather than doing nothing silently).
     *
     * <p>Placeholders: {@code {player}} (name), {@code {uuid}}, {@code {amount}}, {@code {key}},
     * {@code {node}}, {@code {value}}.
     */
    public static class Templates {
        public String crateKey = "";
        public String permission = "lp user {uuid} permission set {node} true";
        public String pokemon = "";
        public String cosmetic = "";
        public String cobbledollarsDeposit = "";
        public String cobbledollarsWithdraw = "";
    }

    public static RewardsConfig defaults() {
        RewardsConfig config = new RewardsConfig();

        // A sample definition so the file is self-documenting on first run.
        RewardDefinition sample = new RewardDefinition();
        sample.displayName = "Sample Tier 1 Reward";
        sample.repeatable = false;
        RewardEntry item = new RewardEntry();
        item.type = "item";
        item.item = "minecraft:diamond";
        item.amount = 3;
        RewardEntry tokens = new RewardEntry();
        tokens.type = "currency";
        tokens.currency = "event_tokens";
        tokens.amount = 500;
        sample.rewards.add(item);
        sample.rewards.add(tokens);
        config.definitions.put("sample_tier_1", sample);

        return config;
    }
}
