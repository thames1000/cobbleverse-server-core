package com.thamescape.cobbleverse.core.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The game-event ingestion bus. Producers {@link #publish} events; registered
 * {@link GameEventListener}s receive them. Dispatch is synchronous (on the publishing thread) and
 * exception-isolated: one listener throwing never stops the others or the producer.
 *
 * <p>Synchronous dispatch is deliberate for now — listeners route their own heavy work (database
 * writes) off-thread. An async queue can slot in behind {@link #publish} later without changing the
 * producer or listener contracts.
 *
 * <p>A built-in debug mode logs every event (toggled by {@code /cvcore debug events on|off}) — the
 * fastest way to see what the game is emitting and which listeners are attached.
 */
public final class GameEventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/GAME");

    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong published = new AtomicLong();
    private volatile boolean debug;

    public void register(GameEventListener listener) {
        listeners.add(listener);
        LOGGER.debug("Registered game-event listener: {}", listener.name());
    }

    public int listenerCount() {
        return listeners.size();
    }

    public long publishedCount() {
        return published.get();
    }

    public void setDebug(boolean enabled) {
        this.debug = enabled;
    }

    public boolean isDebug() {
        return debug;
    }

    /**
     * Publishes an event to every listener. Each listener's {@link Exception}s are caught and logged,
     * so one misbehaving listener cannot disrupt the others or the producer. Note this isolates
     * listener {@code Exception}s only: a {@code null} event throws {@link NullPointerException}, and a
     * listener {@link Error} (e.g. {@code OutOfMemoryError}) still propagates.
     */
    public void publish(GameEvent event) {
        Objects.requireNonNull(event, "event");
        published.incrementAndGet();
        if (debug) {
            LOGGER.info("[GameEvent] {} source={} player={} consumers={} meta={}",
                    event.type(), event.source(), event.playerUuid(), listeners.size(), event.metadata());
        }
        for (GameEventListener listener : listeners) {
            try {
                listener.onGameEvent(event);
            } catch (Exception e) {
                LOGGER.warn("Game-event listener '{}' failed on {}",
                        listener.name(), event.type(), e);
            }
        }
    }
}
