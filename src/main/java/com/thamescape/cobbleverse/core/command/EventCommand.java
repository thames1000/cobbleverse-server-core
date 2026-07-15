package com.thamescape.cobbleverse.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.thamescape.cobbleverse.core.bootstrap.CoreServices;
import com.thamescape.cobbleverse.core.event.EventDefinition;
import com.thamescape.cobbleverse.core.event.EventParticipant;
import com.thamescape.cobbleverse.core.event.EventService;
import com.thamescape.cobbleverse.core.permission.CorePermissions;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Player-facing event commands: {@code /events} (list), {@code /event info|join|leave|leaderboard}.
 * Admin lifecycle control lives under {@code /cvcore event}.
 */
public final class EventCommand {

    private EventCommand() {
    }

    public static final SuggestionProvider<ServerCommandSource> ID_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(CoreServices.events().definitionIds(), builder);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        PermissionService perms = CoreServices.permissions();

        dispatcher.register(literal("events")
                .requires(perms.require(CorePermissions.COMMAND_EVENTS, 0))
                .executes(EventCommand::list));

        dispatcher.register(literal("event")
                .requires(perms.require(CorePermissions.COMMAND_EVENTS, 0))
                .then(literal("info").then(argument("id", StringArgumentType.word())
                        .suggests(ID_SUGGESTIONS).executes(EventCommand::info)))
                .then(literal("join")
                        .requires(perms.require(CorePermissions.EVENT_JOIN, 0))
                        .then(argument("id", StringArgumentType.word())
                                .suggests(ID_SUGGESTIONS).executes(EventCommand::join)))
                .then(literal("leave")
                        .requires(perms.require(CorePermissions.EVENT_LEAVE, 0))
                        .then(argument("id", StringArgumentType.word())
                                .suggests(ID_SUGGESTIONS).executes(EventCommand::leave)))
                .then(literal("leaderboard").then(argument("id", StringArgumentType.word())
                        .suggests(ID_SUGGESTIONS).executes(EventCommand::leaderboard))));
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        EventService events = CoreServices.events();
        source.sendFeedback(() -> CoreServices.messages().prefix().append(Text.literal("Events")), false);
        List<String> ids = events.definitionIds();
        if (ids.isEmpty()) {
            line(source, "(none configured)");
            return 1;
        }
        for (String id : ids) {
            events.definition(id).ifPresent(def -> line(source, id + " — " + def.displayNameOrId()
                    + " [" + events.state(id) + ", " + events.participantCount(id) + " joined]"));
        }
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        EventDefinition def = CoreServices.events().definition(id).orElse(null);
        if (def == null) {
            source.sendError(Text.literal("No event '" + id + "'."));
            return 0;
        }
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal(def.displayNameOrId())), false);
        line(source, def.description);
        line(source, "type: " + def.type + ", state: " + CoreServices.events().state(id));
        line(source, "participants: " + CoreServices.events().participantCount(id));
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            line(source, "you: " + (CoreServices.events().isParticipant(player.getUuid(), id)
                    ? "joined" : "not joined"));
        }
        return 1;
    }

    private static int join(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only players can join. Use /cvcore event addplayer <id> <player>."));
            return 0;
        }
        return report(source, CoreServices.events().join(player.getUuid(),
                StringArgumentType.getString(ctx, "id")));
    }

    private static int leave(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only players can leave an event."));
            return 0;
        }
        return report(source, CoreServices.events().leave(player.getUuid(),
                StringArgumentType.getString(ctx, "id")));
    }

    private static int leaderboard(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        List<EventParticipant> top = CoreServices.events().leaderboard(id, 10);
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Leaderboard — " + id)), false);
        if (top.isEmpty()) {
            line(source, "(no participants)");
            return 1;
        }
        int rank = 1;
        for (EventParticipant p : top) {
            line(source, (rank++) + ". " + p.label() + " — " + p.score());
        }
        return 1;
    }

    /** Sends an event action result to a source; used by player and admin commands. */
    public static int report(ServerCommandSource source, EventService.Result result) {
        if (result.ok()) {
            source.sendFeedback(() -> CoreServices.messages().prefix()
                    .append(Text.literal(result.message())), false);
            return 1;
        }
        source.sendError(Text.literal(result.message()));
        return 0;
    }

    private static void line(ServerCommandSource source, String text) {
        source.sendFeedback(() -> Text.literal("  " + text), false);
    }
}
