package com.thamescape.cobbleverse.core.util;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the running {@link MinecraftServer} so components created at mod-init (before the server
 * exists) can reach it later. Set on server start, cleared on stop.
 */
public final class ServerHolder {

    private static volatile MinecraftServer server;

    private ServerHolder() {
    }

    public static void set(MinecraftServer value) {
        server = value;
    }

    public static void clear() {
        server = null;
    }

    @Nullable
    public static MinecraftServer get() {
        return server;
    }
}
