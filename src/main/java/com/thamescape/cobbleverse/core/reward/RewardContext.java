package com.thamescape.cobbleverse.core.reward;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Everything a reward handler needs to execute one reward for one player. Handlers are only invoked
 * when the player is online, so {@link #player()} is non-null during execution.
 */
public record RewardContext(UUID uuid, ServerPlayerEntity player, MinecraftServer server, String source) {

    /** The player's current name, for command placeholders. */
    public String playerName() {
        return player.getGameProfile().getName();
    }
}
