package com.thamescape.cobbleverse.core.util;

import net.minecraft.server.MinecraftServer;

import java.util.Map;

/**
 * Runs commands as the server console (operator level) and fills {@code {placeholder}} tokens in
 * command templates. Used by command-type rewards and command-backed currency providers.
 */
public final class CommandRunner {

    private CommandRunner() {
    }

    /** Executes {@code command} on the server thread as the server command source. */
    public static void runConsole(MinecraftServer server, String command) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
    }

    /** Replaces {@code {key}} tokens in {@code template} with the given values. */
    public static String fill(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}
