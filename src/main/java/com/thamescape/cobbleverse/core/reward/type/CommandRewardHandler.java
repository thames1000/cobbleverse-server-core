package com.thamescape.cobbleverse.core.reward.type;

import com.thamescape.cobbleverse.core.reward.RewardContext;
import com.thamescape.cobbleverse.core.reward.RewardEntry;
import com.thamescape.cobbleverse.core.reward.RewardHandler;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import com.thamescape.cobbleverse.core.reward.RewardType;
import com.thamescape.cobbleverse.core.util.CommandRunner;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Runs a server command with {@code {player}} / {@code {uuid}} placeholders as the console. */
public final class CommandRewardHandler implements RewardHandler {

    @Override
    public RewardType type() {
        return RewardType.COMMAND;
    }

    @Override
    @Nullable
    public String validate(RewardEntry entry) {
        return (entry.command == null || entry.command.isBlank()) ? "command reward is missing 'command'" : null;
    }

    @Override
    public RewardResult execute(RewardEntry entry, RewardContext context) {
        String command = CommandRunner.fill(entry.command, Map.of(
                "player", context.playerName(),
                "uuid", context.uuid().toString()));
        CommandRunner.runConsole(context.server(), command);
        return RewardResult.success(describe(entry));
    }

    @Override
    public String describe(RewardEntry entry) {
        return "run: " + entry.command;
    }
}
