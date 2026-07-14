package com.thamescape.cobbleverse.core.player;

import com.thamescape.cobbleverse.core.reward.RewardService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Bridges Fabric join/leave events to the {@link PlayerProfileService} and delivers any queued
 * rewards on join. Registered once during mod init; the callbacks fire on the server thread.
 */
public final class PlayerLifecycleListener {

    private final PlayerProfileService profiles;
    private final RewardService rewards;

    public PlayerLifecycleListener(PlayerProfileService profiles, RewardService rewards) {
        this.profiles = profiles;
        this.rewards = rewards;
    }

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            profiles.onJoin(player.getUuid(), player.getGameProfile().getName(),
                    System.currentTimeMillis());
            int delivered = rewards.deliverQueued(player);
            if (delivered > 0) {
                player.sendMessage(Text.literal("Delivered " + delivered + " pending reward(s)."), false);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            profiles.onLeave(player.getUuid(), System.currentTimeMillis());
        });
    }
}
