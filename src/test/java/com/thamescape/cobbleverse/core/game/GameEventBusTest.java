package com.thamescape.cobbleverse.core.game;

import com.thamescape.cobbleverse.core.game.capture.PokemonCapturedGameEvent;
import com.thamescape.cobbleverse.core.game.player.PlayerJoinedGameEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the game-event bus dispatch, exception isolation, and event record contracts. */
class GameEventBusTest {

    private static PokemonCapturedGameEvent capture() {
        return new PokemonCapturedGameEvent(UUID.randomUUID(), Instant.now(), "pikachu", true);
    }

    @Test
    void publishReachesAllListeners() {
        GameEventBus bus = new GameEventBus();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        bus.register(e -> a.add(e.type()));
        bus.register(e -> b.add(e.type()));

        bus.publish(capture());

        assertEquals(List.of("pokemon_captured"), a);
        assertEquals(List.of("pokemon_captured"), b);
        assertEquals(2, bus.listenerCount());
        assertEquals(1, bus.publishedCount());
    }

    @Test
    void aThrowingListenerDoesNotStopOthers() {
        GameEventBus bus = new GameEventBus();
        List<String> received = new ArrayList<>();
        bus.register(e -> {
            throw new RuntimeException("boom");
        });
        bus.register(e -> received.add(e.type()));

        bus.publish(capture()); // must not throw

        assertEquals(1, received.size(), "the well-behaved listener still receives the event");
    }

    @Test
    void eventRecordsCarryTheirContract() {
        PokemonCapturedGameEvent capture = capture();
        assertEquals("pokemon_captured", capture.type());
        assertEquals("cobblemon", capture.source());
        assertEquals("pikachu", capture.metadata().get("species"));
        assertEquals(true, capture.metadata().get("shiny"));

        PlayerJoinedGameEvent join = new PlayerJoinedGameEvent(UUID.randomUUID(), Instant.now(), "Steve");
        assertEquals("player_joined", join.type());
        assertEquals("minecraft", join.source());
        assertEquals("Steve", join.metadata().get("name"));
    }

    @Test
    void debugFlagTogglesWithoutAffectingDispatch() {
        GameEventBus bus = new GameEventBus();
        List<String> received = new ArrayList<>();
        bus.register(e -> received.add(e.type()));

        assertTrue(!bus.isDebug());
        bus.setDebug(true);
        assertTrue(bus.isDebug());
        bus.publish(capture());
        assertEquals(1, received.size(), "debug logging does not change what listeners receive");
    }
}
