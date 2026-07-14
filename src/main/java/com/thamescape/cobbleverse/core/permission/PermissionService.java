package com.thamescape.cobbleverse.core.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

/**
 * Thin wrapper over {@code fabric-permissions-api}. Every check carries a fallback operator level so
 * that when no permission provider (LuckPerms, etc.) is installed, operators retain access.
 */
public final class PermissionService {

    /**
     * Checks a permission node for a command source.
     *
     * @param source        the command source (player or console)
     * @param node          the permission node, e.g. {@code cobbleverse.admin.reload}
     * @param fallbackLevel operator level (0-4) required when no provider answers
     * @return true if the source has the permission
     */
    public boolean check(ServerCommandSource source, String node, int fallbackLevel) {
        return Permissions.check(source, node, fallbackLevel);
    }

    /** Builds a predicate for use with Brigadier's {@code .requires(...)}. */
    public Predicate<ServerCommandSource> require(String node, int fallbackLevel) {
        return source -> Permissions.check(source, node, fallbackLevel);
    }
}
