package com.thamescape.cobbleverse.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thamescape.cobbleverse.core.bootstrap.CoreServices;
import com.thamescape.cobbleverse.core.player.PlayerProfile;
import com.thamescape.cobbleverse.core.permission.CorePermissions;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import com.thamescape.cobbleverse.core.util.TimeFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.ZoneId;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /profile} shows your own server profile; {@code /profile <player>} shows another player's
 * (online or offline, by last known name). Read-only.
 */
public final class ProfileCommand {

    private ProfileCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        PermissionService perms = CoreServices.permissions();

        dispatcher.register(literal("profile")
                .requires(perms.require(CorePermissions.COMMAND_PROFILE, 0))
                .executes(ProfileCommand::self)
                .then(argument("player", StringArgumentType.word())
                        .requires(perms.require(CorePermissions.PROFILE_VIEW_OTHER, 2))
                        .executes(ProfileCommand::other)));
    }

    private static int self(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only players can view their own profile. "
                    + "Use /profile <player> from the console."));
            return 0;
        }
        Optional<PlayerProfile> profile = CoreServices.players().find(player.getUuid());
        return show(source, profile.orElse(null), player.getGameProfile().getName());
    }

    private static int other(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "player");

        // Prefer an online player (authoritative UUID), else fall back to a stored name lookup.
        MinecraftServer server = source.getServer();
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        Optional<PlayerProfile> profile = online != null
                ? CoreServices.players().find(online.getUuid())
                : CoreServices.players().findByName(name);

        if (profile.isEmpty()) {
            source.sendError(Text.literal("No profile found for '" + name + "'."));
            return 0;
        }
        return show(source, profile.get(), name);
    }

    private static int show(ServerCommandSource source, PlayerProfile profile, String fallbackName) {
        if (profile == null) {
            // Brand-new player whose profile hasn't been created yet (shouldn't normally happen).
            source.sendError(Text.literal("No profile data yet for " + fallbackName + "."));
            return 0;
        }
        ZoneId zone = zone();
        String name = profile.lastKnownName() != null ? profile.lastKnownName() : fallbackName;

        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Profile — " + name)), false);
        line(source, "UUID: " + profile.uuid());
        line(source, "First joined: " + TimeFormat.timestamp(profile.firstJoinedAt(), zone));
        line(source, "Last joined: " + TimeFormat.timestamp(profile.lastJoinedAt(), zone));
        line(source, "Playtime: " + TimeFormat.playtime(profile.playtimeSeconds()));
        return 1;
    }

    private static void line(ServerCommandSource source, String text) {
        source.sendFeedback(() -> Text.literal("  " + text), false);
    }

    private static ZoneId zone() {
        try {
            return ZoneId.of(CoreServices.config().core().timezone);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
