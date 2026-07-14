package com.thamescape.cobbleverse.core.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Hooks core commands into Fabric's command registration event. Called once during mod init; the
 * actual command tree lives in {@link CoreCommand}.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CoreCommand.register(dispatcher));
    }
}
