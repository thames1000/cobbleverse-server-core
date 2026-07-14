package com.thamescape.cobbleverse.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.thamescape.cobbleverse.core.bootstrap.CoreServices;
import com.thamescape.cobbleverse.core.permission.CorePermissions;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import com.thamescape.cobbleverse.core.reward.RewardDefinition;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Player-facing rewards: {@code /rewards} (list), {@code /rewards claim <id>}, {@code /rewards
 * preview <id>}. Admin granting lives under {@code /cvcore reward}.
 */
public final class RewardCommand {

    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    private RewardCommand() {
    }

    /** Suggests known reward definition ids. */
    public static final SuggestionProvider<ServerCommandSource> ID_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(CoreServices.rewards().definitionIds(), builder);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        PermissionService perms = CoreServices.permissions();

        dispatcher.register(literal("rewards")
                .requires(perms.require(CorePermissions.COMMAND_REWARDS, 0))
                .executes(RewardCommand::list)
                .then(literal("claim")
                        .requires(perms.require(CorePermissions.REWARD_CLAIM, 0))
                        .then(argument("id", StringArgumentType.word())
                                .suggests(ID_SUGGESTIONS)
                                .executes(RewardCommand::claim)))
                .then(literal("preview")
                        .requires(perms.require(CorePermissions.REWARD_PREVIEW, 0))
                        .then(argument("id", StringArgumentType.word())
                                .suggests(ID_SUGGESTIONS)
                                .executes(RewardCommand::preview))));
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        UUID uuid = player != null ? player.getUuid() : CONSOLE_UUID;

        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Rewards")), false);
        var ids = CoreServices.rewards().definitionIds();
        if (ids.isEmpty()) {
            source.sendFeedback(() -> Text.literal("  (none configured)"), false);
            return 1;
        }
        for (String id : ids) {
            RewardDefinition def = CoreServices.rewards().definition(id).orElse(null);
            if (def == null) {
                continue;
            }
            String state = def.repeatable ? "repeatable"
                    : (player != null && CoreServices.rewards().hasClaimed(uuid, id)) ? "claimed" : "unclaimed";
            source.sendFeedback(() -> Text.literal("  " + id + " — " + def.displayNameOrId()
                    + " [" + state + "]"), false);
        }
        return 1;
    }

    private static int claim(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only players can claim rewards. Use /cvcore reward grant."));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        RewardResult result = CoreServices.rewards().grant(player.getUuid(), id, "self_claim");
        report(source, result);
        return result.ok() ? 1 : 0;
    }

    private static int preview(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        UUID uuid = player != null ? player.getUuid() : CONSOLE_UUID;
        String id = StringArgumentType.getString(ctx, "id");
        report(source, CoreServices.rewards().preview(uuid, id));
        return 1;
    }

    /** Sends a reward result (header line + per-entry detail) to a command source. */
    public static void report(ServerCommandSource source, RewardResult result) {
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal(result.status() + ": " + result.message())), false);
        for (String line : result.lines()) {
            source.sendFeedback(() -> Text.literal("  " + line), false);
        }
    }
}
