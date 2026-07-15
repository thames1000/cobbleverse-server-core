package com.thamescape.cobbleverse.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thamescape.cobbleverse.core.bootstrap.CoreServices;
import com.thamescape.cobbleverse.core.permission.CorePermissions;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import com.thamescape.cobbleverse.core.season.Milestone;
import com.thamescape.cobbleverse.core.season.ObjectiveDefinition;
import com.thamescape.cobbleverse.core.season.ObjectiveProgress;
import com.thamescape.cobbleverse.core.season.SeasonDefinition;
import com.thamescape.cobbleverse.core.season.SeasonProgress;
import com.thamescape.cobbleverse.core.season.SeasonService;
import com.thamescape.cobbleverse.core.season.SeasonState;
import com.thamescape.cobbleverse.core.util.TimeFormat;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/** Player-facing season commands: {@code /season} (overview) and {@code /season progress}. */
public final class SeasonCommand {

    private SeasonCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        PermissionService perms = CoreServices.permissions();

        dispatcher.register(literal("season")
                .requires(perms.require(CorePermissions.COMMAND_SEASON, 0))
                .executes(SeasonCommand::overview)
                .then(literal("progress")
                        .requires(perms.require(CorePermissions.SEASON_PROGRESS, 0))
                        .executes(SeasonCommand::progress))
                .then(literal("leaderboard")
                        .executes(SeasonCommand::leaderboard)));
    }

    private static int leaderboard(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        SeasonDefinition def = CoreServices.seasons().configuredSeason().orElse(null);
        if (def == null) {
            source.sendError(Text.literal("No active season."));
            return 0;
        }
        var top = CoreServices.seasons().leaderboard(def.id, 10);
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Season leaderboard — " + def.displayNameOrId())), false);
        if (top.isEmpty()) {
            line(source, "(no points yet)");
            return 1;
        }
        int rank = 1;
        for (var e : top) {
            line(source, (rank++) + ". " + e.label() + " — " + e.points());
        }
        return 1;
    }

    private static int overview(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        SeasonService seasons = CoreServices.seasons();
        SeasonDefinition def = seasons.configuredSeason().orElse(null);
        if (def == null) {
            source.sendFeedback(() -> CoreServices.messages().prefix()
                    .append(Text.literal("No active season.")), false);
            return 1;
        }
        SeasonState state = seasons.state(def);
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Season: " + def.displayNameOrId() + " [" + state + "]")), false);

        if (state == SeasonState.ACTIVE) {
            line(source, "Ends in: " + remaining(def.endsAt));
        } else if (state == SeasonState.UPCOMING) {
            line(source, "Starts in: " + remaining(def.startsAt));
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            line(source, "Your points: " + seasons.points(player.getUuid(), def.id));
        }
        return 1;
    }

    private static int progress(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only players can view their own progress. "
                    + "Use /cvcore season progress <player>."));
            return 0;
        }
        SeasonDefinition def = CoreServices.seasons().configuredSeason().orElse(null);
        if (def == null) {
            source.sendError(Text.literal("No active season."));
            return 0;
        }
        report(source, def, CoreServices.seasons().progress(player.getUuid(), def.id));
        return 1;
    }

    /** Renders a player's progress against a season definition. Shared with the admin command. */
    public static void report(ServerCommandSource source, SeasonDefinition def, SeasonProgress progress) {
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Season progress — " + def.displayNameOrId())), false);
        line(source, "Points: " + progress.points());
        for (ObjectiveDefinition objective : def.objectives) {
            ObjectiveProgress op = progress.objectives().get(objective.id);
            int done = op == null ? 0 : op.progress();
            boolean complete = op != null && op.completed();
            line(source, "- " + objective.displayNameOrId() + ": " + done + "/" + objective.required
                    + (complete ? " [complete]" : ""));
        }
        nextMilestone(def, progress.points()).ifPresent(m ->
                line(source, "Next milestone: '" + m.reward + "' at " + m.points + " points ("
                        + (m.points - progress.points()) + " to go)"));
    }

    private static java.util.Optional<Milestone> nextMilestone(SeasonDefinition def, int points) {
        return def.milestones.stream()
                .filter(m -> m.points > points)
                .min(java.util.Comparator.comparingInt(m -> m.points));
    }

    private static String remaining(String iso) {
        try {
            Instant target = OffsetDateTime.parse(iso).toInstant();
            long seconds = Duration.between(Instant.now(), target).getSeconds();
            return seconds <= 0 ? "now" : TimeFormat.playtime(seconds);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static void line(ServerCommandSource source, String text) {
        source.sendFeedback(() -> Text.literal("  " + text), false);
    }

    /** Suggests objective ids from the active season (for the admin objective command). */
    public static java.util.List<String> activeObjectiveIds() {
        return CoreServices.seasons().configuredSeason()
                .map(def -> def.objectives.stream().map(o -> o.id).toList())
                .orElse(java.util.List.of());
    }

    /** Renders a player's UUID-based progress for the admin command. */
    public static void reportFor(ServerCommandSource source, UUID uuid) {
        SeasonDefinition def = CoreServices.seasons().configuredSeason().orElse(null);
        if (def == null) {
            source.sendError(Text.literal("No active season."));
            return;
        }
        report(source, def, CoreServices.seasons().progress(uuid, def.id));
    }
}
