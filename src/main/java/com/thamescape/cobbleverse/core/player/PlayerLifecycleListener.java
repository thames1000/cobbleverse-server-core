package com.thamescape.cobbleverse.core.player;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Bridges Fabric join/leave events to the {@link PlayerProfileService}. Registered once during mod
 * init; the callbacks fire on the server thread.
 */
public final class PlayerLifecycleListener {

    private final PlayerProfileService profiles;

    public PlayerLifecycleListener(PlayerProfileService profiles) {
        this.profiles = profiles;
    }

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            profiles.onJoin(player.getUuid(), player.getGameProfile().getName(),
                    System.currentTimeMillis());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            profiles.onLeave(player.getUuid(), System.currentTimeMillis());
        });
    }
}
