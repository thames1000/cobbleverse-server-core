package com.thamescape.cobbleverse.core.scheduler;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central place for repeating and delayed server tasks, so timers aren't scattered across the
 * codebase. Tasks run on the server thread at the end of each tick; a task that needs the database
 * dispatches to the async database worker itself.
 *
 * <p>0.2.0 supports tick-based scheduling (every N ticks / once after a delay). Wall-clock and
 * daily/weekly scheduling arrive with the season system.
 */
public final class CoreScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/SCHEDULER");
    public static final int TICKS_PER_SECOND = 20;

    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private volatile long currentTick;
    private int failures;

    /** Registers the per-tick driver. Call once during mod init. */
    public void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> onTick());
    }

    /** Runs {@code action} every {@code periodTicks} ticks, starting one period from now. */
    public void scheduleRepeating(String id, long periodTicks, Runnable action) {
        tasks.put(id, new ScheduledTask(id, periodTicks, true, currentTick + periodTicks, action));
    }

    /** Runs {@code action} once after {@code delayTicks} ticks. */
    public void scheduleOnce(String id, long delayTicks, Runnable action) {
        tasks.put(id, new ScheduledTask(id, delayTicks, false, currentTick + delayTicks, action));
    }

    public boolean cancel(String id) {
        return tasks.remove(id) != null;
    }

    public List<String> activeTaskIds() {
        return new ArrayList<>(tasks.keySet());
    }

    public int failedRuns() {
        return failures;
    }

    private void onTick() {
        currentTick++;
        for (ScheduledTask task : tasks.values()) {
            if (!task.isDue(currentTick)) {
                continue;
            }
            try {
                task.run();
            } catch (Exception e) {
                failures++;
                LOGGER.warn("Scheduled task '{}' failed: {}", task.id(), e.getMessage());
            }
            if (task.repeating()) {
                task.reschedule(currentTick);
            } else {
                tasks.remove(task.id());
            }
        }
    }
}
