package com.thamescape.cobbleverse.core.reward.type;

import com.thamescape.cobbleverse.core.reward.RewardContext;
import com.thamescape.cobbleverse.core.reward.RewardEntry;
import com.thamescape.cobbleverse.core.reward.RewardHandler;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import com.thamescape.cobbleverse.core.reward.RewardType;
import com.thamescape.cobbleverse.core.util.CommandRunner;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Delivers a reward by running a configurable command template, for types backed by another mod:
 * {@code crate_key} (SkiesCrates), {@code permission} (LuckPerms), {@code pokemon} (Cobblemon),
 * {@code cosmetic}. The template is read live from config; a blank template means the type is
 * unsupported on this server and the reward reports that clearly rather than silently doing nothing.
 *
 * <p>Placeholders available to templates: {@code {player}}, {@code {uuid}}, {@code {amount}}, and the
 * type's own field ({@code {key}} / {@code {node}} / {@code {value}}).
 */
public final class CommandTemplateRewardHandler implements RewardHandler {

    private final RewardType type;
    private final Supplier<String> templateSupplier;
    private final String fieldName;
    private final Function<RewardEntry, String> fieldAccessor;
    private final Function<RewardEntry, String> describer;

    public CommandTemplateRewardHandler(RewardType type,
                                        Supplier<String> templateSupplier,
                                        String fieldName,
                                        Function<RewardEntry, String> fieldAccessor,
                                        Function<RewardEntry, String> describer) {
        this.type = type;
        this.templateSupplier = templateSupplier;
        this.fieldName = fieldName;
        this.fieldAccessor = fieldAccessor;
        this.describer = describer;
    }

    @Override
    public RewardType type() {
        return type;
    }

    @Override
    @Nullable
    public String validate(RewardEntry entry) {
        String value = fieldAccessor.apply(entry);
        return (value == null || value.isBlank())
                ? type.id() + " reward is missing '" + fieldName + "'"
                : null;
    }

    @Override
    public RewardResult execute(RewardEntry entry, RewardContext context) {
        String template = templateSupplier.get();
        if (template == null || template.isBlank()) {
            return RewardResult.unsupported("no command template configured for '" + type.id()
                    + "' (set templates in rewards.json)");
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", context.playerName());
        placeholders.put("uuid", context.uuid().toString());
        placeholders.put("amount", Long.toString(entry.amount));
        String value = fieldAccessor.apply(entry);
        placeholders.put(fieldName, value == null ? "" : value);

        CommandRunner.runConsole(context.server(), CommandRunner.fill(template, placeholders));
        return RewardResult.success(describe(entry));
    }

    @Override
    public String describe(RewardEntry entry) {
        return describer.apply(entry);
    }
}
