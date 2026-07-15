package com.thamescape.cobbleverse.core.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thamescape.cobbleverse.core.CoreConstants;
import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.bootstrap.CoreServices;
import com.thamescape.cobbleverse.core.config.CoreConfig;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckResult;
import com.thamescape.cobbleverse.core.diagnostics.HealthStatus;
import com.thamescape.cobbleverse.core.integration.IntegrationReport;
import com.thamescape.cobbleverse.core.message.MessageKey;
import com.thamescape.cobbleverse.core.permission.CorePermissions;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import com.thamescape.cobbleverse.core.persistence.MigrationManager;
import com.thamescape.cobbleverse.core.player.PlayerProfileService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Implements the {@code /cvcore} command tree: {@code info}, {@code health}, {@code integrations},
 * {@code reload} and {@code debug}. Output goes through {@link com.thamescape.cobbleverse.core.message.MessageService};
 * administrative subcommands are audited.
 */
public final class CoreCommand {

    private CoreCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        PermissionService perms = CoreServices.permissions();

        LiteralArgumentBuilder<ServerCommandSource> root = literal("cvcore")
                .requires(perms.require(CorePermissions.COMMAND_CVCORE, 2))
                .executes(ctx -> info(ctx.getSource()));

        root.then(literal("info")
                .executes(ctx -> info(ctx.getSource())));

        root.then(literal("health")
                .executes(ctx -> health(ctx.getSource())));

        root.then(literal("integrations")
                .executes(ctx -> integrations(ctx.getSource())));

        root.then(literal("reload")
                .requires(perms.require(CorePermissions.ADMIN_RELOAD, CoreConstants.ADMIN_FALLBACK_LEVEL))
                .executes(ctx -> reload(ctx.getSource())));

        root.then(literal("debug")
                .requires(perms.require(CorePermissions.ADMIN_DEBUG, CoreConstants.ADMIN_FALLBACK_LEVEL))
                .executes(ctx -> debug(ctx.getSource())));

        root.then(literal("database")
                .requires(perms.require(CorePermissions.ADMIN_DATABASE, CoreConstants.ADMIN_FALLBACK_LEVEL))
                .then(literal("status")
                        .executes(ctx -> databaseStatus(ctx.getSource()))));

        root.then(literal("player")
                .requires(perms.require(CorePermissions.ADMIN_PLAYER, CoreConstants.ADMIN_FALLBACK_LEVEL))
                .then(literal("create")
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> playerCreate(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"))))));

        root.then(literal("reward")
                .requires(perms.require(CorePermissions.ADMIN_REWARDS, CoreConstants.ADMIN_FALLBACK_LEVEL))
                .then(literal("list")
                        .executes(ctx -> rewardList(ctx.getSource())))
                .then(literal("grant")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("id", StringArgumentType.word())
                                        .suggests(RewardCommand.ID_SUGGESTIONS)
                                        .executes(ctx -> rewardGrant(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(literal("retry")
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> rewardRetry(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"), null))
                                .then(argument("id", StringArgumentType.word())
                                        .suggests(RewardCommand.ID_SUGGESTIONS)
                                        .executes(ctx -> rewardRetry(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(literal("queue")
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> rewardQueue(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"))))));

        root.then(literal("season")
                .requires(perms.require(CorePermissions.ADMIN_SEASON, CoreConstants.ADMIN_FALLBACK_LEVEL))
                .then(literal("info")
                        .executes(ctx -> seasonInfo(ctx.getSource())))
                .then(literal("progress")
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> seasonProgress(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))
                .then(literal("addpoints")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> seasonAddPoints(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(literal("objective")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("objective", StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.command.CommandSource
                                                .suggestMatching(SeasonCommand.activeObjectiveIds(), b))
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> seasonObjective(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "objective"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"))))))));

        dispatcher.register(root);
    }

    private static int info(ServerCommandSource source) {
        CoreConfig config = CoreServices.config().core();
        long available = CoreServices.integrations().reports().stream()
                .filter(IntegrationReport::available).count();

        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal(CoreConstants.MOD_NAME + " v" + version())), false);
        line(source, "Server: " + config.serverId + " (" + config.environment + ")");
        line(source, "Storage: " + storageLabel());
        line(source, "Active season: " + (config.activeSeason.isBlank() ? "<none>" : config.activeSeason));
        line(source, "Integrations: " + available + " available");
        return 1;
    }

    private static int health(ServerCommandSource source) {
        List<HealthCheckResult> results = CoreServices.health().runAll();
        HealthStatus overall = CoreServices.health().overall(results);

        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Health: " + overall.name())), false);
        for (HealthCheckResult r : results) {
            line(source, r.name() + ": " + r.status().name() + " — " + r.detail());
        }
        return overall == HealthStatus.ERROR ? 0 : 1;
    }

    private static int integrations(ServerCommandSource source) {
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Integrations")), false);
        for (IntegrationReport r : CoreServices.integrations().reports()) {
            String status = r.available() && r.version() != null
                    ? "available (" + r.version() + ")"
                    : r.status().name().toLowerCase();
            line(source, r.displayName() + ": " + status);
        }
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        List<String> problems = CoreServices.config().reload();
        CoreServices.messages().reload();
        CoreServices.integrations().detectAll();

        boolean ok = problems.isEmpty();
        CoreServices.audit().record(AuditEntry.builder(AuditType.CONFIG_RELOAD)
                .actor(actorUuid(source), source.getName())
                .source("admin_command")
                .context(ok ? "core+messages reloaded" : String.join("; ", problems)));

        if (ok) {
            source.sendFeedback(() -> CoreServices.messages().prefixed(MessageKey.RELOAD_SUCCESS), false);
        } else {
            source.sendError(CoreServices.messages().prefixed(
                    MessageKey.RELOAD_FAILED, Map.of("reason", problems.get(0))));
        }
        return ok ? 1 : 0;
    }

    private static int debug(ServerCommandSource source) {
        CoreConfig config = CoreServices.config().core();
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Debug")), false);
        line(source, "debug flag: " + config.debug);
        line(source, "audit enabled: " + CoreServices.audit().enabled());
        line(source, "recent audit entries: " + CoreServices.audit().recent().size());
        line(source, "config dir: " + CoreServices.config().loader().configDir());
        line(source, "loader: " + FabricLoader.getInstance().getEnvironmentType());
        return 1;
    }

    private static int databaseStatus(ServerCommandSource source) {
        var db = CoreServices.database();
        var players = CoreServices.players();
        var audit = CoreServices.audit();

        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Database")), false);
        line(source, "Backend: " + db.describe());
        line(source, "Connected: " + (db.isConnected() ? "yes" : "no"));
        line(source, "Schema version: " + MigrationManager.withDefaults().currentVersion(db)
                + " / " + MigrationManager.withDefaults().latestVersion());
        line(source, "Stored profiles: " + players.storedCount());
        line(source, "Online / cached: " + players.onlineCount() + " / " + players.cachedCount());
        line(source, "Profiles pending flush: " + players.dirtyCount());
        line(source, "DB writes queued: " + db.pending());
        long auditRows = audit.storedCount();
        line(source, "Audit rows: " + (auditRows < 0 ? "n/a" : auditRows));
        return 1;
    }

    /**
     * Pre-creates a profile for a player who has not joined. Resolves the UUID the same way the
     * server would: deterministically for offline-mode, or via an async Mojang lookup for online-mode
     * (so the command never blocks the server thread on the network).
     */
    private static int playerCreate(ServerCommandSource source, String name) {
        MinecraftServer server = source.getServer();
        long now = System.currentTimeMillis();

        if (!server.isOnlineMode()) {
            UUID uuid = Uuids.getOfflinePlayerUuid(name);
            PlayerProfileService.ProfileCreation result =
                    CoreServices.players().createIfAbsent(uuid, name, now);
            report(source, uuid, name, result);
            return result == PlayerProfileService.ProfileCreation.CREATED ? 1 : 0;
        }

        UserCache userCache = server.getUserCache();
        if (userCache == null) {
            source.sendError(Text.literal("User cache is unavailable; cannot resolve online-mode UUID."));
            return 0;
        }

        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Looking up '" + name + "' with Mojang...")), false);

        userCache.findByNameAsync(name).thenAccept(profile -> {
            if (profile.isEmpty()) {
                server.execute(() -> source.sendError(
                        Text.literal("No Minecraft account found for '" + name + "'.")));
                return;
            }
            GameProfile gameProfile = profile.get();
            UUID uuid = gameProfile.getId();
            String resolvedName = gameProfile.getName();
            PlayerProfileService.ProfileCreation result =
                    CoreServices.players().createIfAbsent(uuid, resolvedName, now);
            server.execute(() -> report(source, uuid, resolvedName, result));
        }).exceptionally(t -> {
            server.execute(() -> source.sendError(
                    Text.literal("Lookup failed: " + t.getMessage())));
            return null;
        });
        return 1;
    }

    private static void report(ServerCommandSource source, UUID uuid, String name,
                               PlayerProfileService.ProfileCreation result) {
        if (result == PlayerProfileService.ProfileCreation.CREATED) {
            CoreServices.audit().record(AuditEntry.builder(AuditType.PLAYER_PROFILE_EDITED)
                    .actor(actorUuid(source), source.getName())
                    .target(uuid)
                    .source("admin_command")
                    .context("pre-created profile for " + name));
            source.sendFeedback(() -> CoreServices.messages().prefix()
                    .append(Text.literal("Created profile for " + name + " (" + uuid + ")")), true);
        } else {
            source.sendFeedback(() -> CoreServices.messages().prefix()
                    .append(Text.literal("Profile for " + name + " already exists (" + uuid + ")")), false);
        }
    }

    private static int rewardList(ServerCommandSource source) {
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Reward definitions")), false);
        var ids = CoreServices.rewards().definitionIds();
        if (ids.isEmpty()) {
            line(source, "(none configured)");
            return 1;
        }
        for (String id : ids) {
            CoreServices.rewards().definition(id).ifPresent(def ->
                    line(source, id + " — " + def.displayNameOrId()
                            + (def.repeatable ? " [repeatable]" : "")
                            + " (" + def.rewards.size() + " entries)"));
        }
        return 1;
    }

    private static int rewardGrant(ServerCommandSource source, String playerName, String id) {
        if (CoreServices.rewards().definition(id).isEmpty()) {
            source.sendError(Text.literal("No reward definition '" + id + "'."));
            return 0;
        }
        String actor = "admin:" + source.getName();
        withResolvedUuid(source, playerName, uuid ->
                RewardCommand.report(source, CoreServices.rewards().grant(uuid, id, actor)));
        return 1;
    }

    private static int rewardRetry(ServerCommandSource source, String playerName, String id) {
        withResolvedUuid(source, playerName, uuid -> {
            var outcome = CoreServices.rewards().retry(uuid, id);
            source.sendFeedback(() -> CoreServices.messages().prefix().append(Text.literal(
                    "Revived " + outcome.revived() + " dead-lettered reward(s); delivered "
                            + outcome.delivered() + (outcome.online() ? "" : " (player offline — "
                            + "revived rewards deliver on next join)"))), true);
        });
        return 1;
    }

    private static int rewardQueue(ServerCommandSource source, String playerName) {
        withResolvedUuid(source, playerName, uuid -> {
            var rows = CoreServices.rewards().queueSnapshot(uuid);
            source.sendFeedback(() -> CoreServices.messages().prefix()
                    .append(Text.literal("Reward queue (" + rows.size() + ")")), false);
            if (rows.isEmpty()) {
                line(source, "(empty)");
            }
            for (var row : rows) {
                line(source, row.definitionId() + " — " + row.status()
                        + " (attempts: " + row.attemptCount() + ", source: " + row.source() + ")");
            }
        });
        return 1;
    }

    private static int seasonInfo(ServerCommandSource source) {
        var seasons = CoreServices.seasons();
        var def = seasons.activeDefinition().orElse(null);
        source.sendFeedback(() -> CoreServices.messages().prefix()
                .append(Text.literal("Season")), false);
        if (def == null) {
            line(source, "active season: <none> (core.json activeSeason='" + seasons.activeSeasonId() + "')");
            return 1;
        }
        line(source, "id: " + def.id + " (" + def.displayNameOrId() + ")");
        line(source, "state: " + seasons.state(def));
        line(source, "window: " + def.startsAt + " -> " + def.endsAt);
        line(source, "objectives: " + def.objectives.size() + ", milestones: " + def.milestones.size());
        return 1;
    }

    private static int seasonProgress(ServerCommandSource source, String playerName) {
        withResolvedUuid(source, playerName, uuid -> SeasonCommand.reportFor(source, uuid));
        return 1;
    }

    private static int seasonAddPoints(ServerCommandSource source, String playerName, int amount) {
        String seasonId = activeSeasonOrError(source);
        if (seasonId == null) {
            return 0;
        }
        withResolvedUuid(source, playerName, uuid -> {
            int total = CoreServices.seasons().addPoints(uuid, seasonId, amount, "admin:" + source.getName());
            source.sendFeedback(() -> CoreServices.messages().prefix().append(Text.literal(
                    "Adjusted points by " + amount + "; new total: " + total)), true);
        });
        return 1;
    }

    private static int seasonObjective(ServerCommandSource source, String playerName,
                                       String objectiveId, int amount) {
        String seasonId = activeSeasonOrError(source);
        if (seasonId == null) {
            return 0;
        }
        withResolvedUuid(source, playerName, uuid -> {
            var result = CoreServices.seasons().addObjectiveProgress(uuid, seasonId, objectiveId, amount);
            source.sendFeedback(() -> CoreServices.messages().prefix().append(Text.literal(
                    "Objective '" + objectiveId + "': " + result.status()
                            + " (progress " + result.progress() + ")")), true);
        });
        return 1;
    }

    @org.jetbrains.annotations.Nullable
    private static String activeSeasonOrError(ServerCommandSource source) {
        var def = CoreServices.seasons().activeDefinition().orElse(null);
        if (def == null) {
            source.sendError(Text.literal("No active season configured (see core.json activeSeason)."));
            return null;
        }
        return def.id;
    }

    /** Resolves a player name to a UUID (online directly, offline via async Mojang lookup) then runs {@code action}. */
    private static void withResolvedUuid(ServerCommandSource source, String playerName,
                                         java.util.function.Consumer<UUID> action) {
        MinecraftServer server = source.getServer();
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(playerName);
        if (online != null) {
            action.accept(online.getUuid());
            return;
        }
        UserCache userCache = server.getUserCache();
        if (userCache == null) {
            source.sendError(Text.literal("Player '" + playerName + "' is offline and no user cache is available."));
            return;
        }
        userCache.findByNameAsync(playerName).thenAccept(profile -> {
            if (profile.isEmpty()) {
                server.execute(() -> source.sendError(Text.literal("No player found named '" + playerName + "'.")));
                return;
            }
            UUID uuid = profile.get().getId();
            server.execute(() -> action.accept(uuid));
        }).exceptionally(t -> {
            server.execute(() -> source.sendError(Text.literal("Lookup failed: " + t.getMessage())));
            return null;
        });
    }

    private static void line(ServerCommandSource source, String text) {
        source.sendFeedback(() -> Text.literal("  " + text), false);
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer(CoreConstants.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String storageLabel() {
        return CoreServices.database().describe();
    }

    private static UUID actorUuid(ServerCommandSource source) {
        Entity entity = source.getEntity();
        return entity != null ? entity.getUuid() : null;
    }
}
