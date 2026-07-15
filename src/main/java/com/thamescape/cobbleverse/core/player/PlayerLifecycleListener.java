package com.thamescape.cobbleverse.core.player;

import com.thamescape.cobbleverse.core.game.GameEventBus;
import com.thamescape.cobbleverse.core.game.player.PlayerJoinedGameEvent;
import com.thamescape.cobbleverse.core.game.player.PlayerLeftGameEvent;
import com.thamescape.cobbleverse.core.reward.RewardService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.Instant;

/**
 * Bridges Fabric join/leave events to the {@link PlayerProfileService}, delivers queued rewards on
 * join, and publishes {@code player_joined} / {@code player_left} game events to the
 * {@link GameEventBus}. Registered once during mod init; callbacks fire on the server thread.
 */
public final class PlayerLifecycleListener {

    private final PlayerProfileService profiles;
    private final RewardService rewards;
    private final GameEventBus gameEvents;

    public PlayerLifecycleListener(PlayerProfileService profiles, RewardService rewards,
                                   GameEventBus gameEvents) {
        this.profiles = profiles;
        this.rewards = rewards;
        this.gameEvents = gameEvents;
    }

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            String name = player.getGameProfile().getName();
            profiles.onJoin(player.getUuid(), name, System.currentTimeMillis());
            int delivered = rewards.deliverQueued(player);
            if (delivered > 0) {
                player.sendMessage(Text.literal("Delivered " + delivered + " pending reward(s)."), false);
            }
            gameEvents.publish(new PlayerJoinedGameEvent(player.getUuid(), Instant.now(), name));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            gameEvents.publish(new PlayerLeftGameEvent(player.getUuid(), Instant.now(),
                    player.getGameProfile().getName()));
            profiles.onLeave(player.getUuid(), System.currentTimeMillis());
        });
    }
}
