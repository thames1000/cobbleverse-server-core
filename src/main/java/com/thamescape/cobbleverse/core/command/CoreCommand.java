package com.thamescape.cobbleverse.core.command;

import com.mojang.brigadier.CommandDispatcher;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static void line(ServerCommandSource source, String text) {
        source.sendFeedback(() -> Text.literal("  " + text), false);
    }

    private static String version() {
        return FabricLoader.getInstance().getModContainer(CoreConstants.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String storageLabel() {
        // 0.1.0 has no persistence yet; the database layer arrives in 0.2.0.
        return "none (in-memory, 0.1.0)";
    }

    private static UUID actorUuid(ServerCommandSource source) {
        Entity entity = source.getEntity();
        return entity != null ? entity.getUuid() : null;
    }
}
