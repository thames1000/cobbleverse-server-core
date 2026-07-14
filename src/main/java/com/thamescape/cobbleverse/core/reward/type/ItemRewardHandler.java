package com.thamescape.cobbleverse.core.reward.type;

import com.thamescape.cobbleverse.core.reward.RewardContext;
import com.thamescape.cobbleverse.core.reward.RewardEntry;
import com.thamescape.cobbleverse.core.reward.RewardHandler;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import com.thamescape.cobbleverse.core.reward.RewardType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Gives an item to the player's inventory (overflow drops at their feet), splitting into stacks. */
public final class ItemRewardHandler implements RewardHandler {

    @Override
    public RewardType type() {
        return RewardType.ITEM;
    }

    @Override
    @Nullable
    public String validate(RewardEntry entry) {
        if (entry.item == null || entry.item.isBlank()) {
            return "item reward is missing 'item'";
        }
        Identifier id = Identifier.tryParse(entry.item);
        if (id == null) {
            return "invalid item id '" + entry.item + "'";
        }
        if (!Registries.ITEM.containsId(id)) {
            return "unknown item '" + entry.item + "' (is the mod that provides it installed?)";
        }
        if (entry.amount <= 0) {
            return "item amount must be positive";
        }
        return null;
    }

    @Override
    public RewardResult execute(RewardEntry entry, RewardContext context) {
        Identifier id = Identifier.tryParse(entry.item);
        Item item = id == null ? null : Registries.ITEM.getOrEmpty(id).orElse(null);
        if (item == null) {
            return RewardResult.failed("unknown item '" + entry.item + "'");
        }
        long remaining = entry.amount;
        int maxStack = new ItemStack(item).getMaxCount();
        while (remaining > 0) {
            int count = (int) Math.min(remaining, maxStack);
            context.player().giveItemStack(new ItemStack(item, count));
            remaining -= count;
        }
        return RewardResult.success(describe(entry));
    }

    @Override
    public String describe(RewardEntry entry) {
        return entry.amount + "x " + entry.item;
    }
}
