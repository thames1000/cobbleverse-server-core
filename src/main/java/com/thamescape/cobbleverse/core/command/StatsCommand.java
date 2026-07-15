package com.thamescape.cobbleverse.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thamescape.cobbleverse.core.bootstrap.CoreServices;
import com.thamescape.cobbleverse.core.permission.CorePermissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/** {@code /stats} — a player's own tracked statistics (captures, shinies, battles won, ...). */
public final class StatsCommand {

    private StatsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("stats")
                .requires(CoreServices.permissions().require(CorePermissions.COMMAND_STATS, 0))
                .executes(StatsCommand::self));
    }

    private static int self(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only players can view their own stats. "
                    + "Use /cvcore player stats <player>."));
            return 0;
        }
        show(source, player.getUuid(), player.getGameProfile().getName());
        return 1;
    }

    /** Renders a player's statistics. Shared with the admin command. */
    public static void show(ServerCommandSource source, UUID uuid, String name) {
        Map<String, Long> stats = CoreServices.statistics().all(uuid);
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Statistics — " + name)), false);
        if (stats.isEmpty()) {
            source.sendFeedback(() -> Text.literal("  (none yet)"), false);
            return;
        }
        stats.forEach((stat, value) ->
                source.sendFeedback(() -> Text.literal("  " + stat + ": " + value), false));
    }
}
