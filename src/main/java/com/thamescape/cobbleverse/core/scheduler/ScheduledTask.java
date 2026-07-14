package com.thamescape.cobbleverse.core.scheduler;

/**
 * A task tracked by {@link CoreScheduler}. Either one-shot (after a delay) or repeating (every N
 * ticks). Tick counts are server ticks (20 per second).
 */
public final class ScheduledTask {

    private final String id;
    private final long periodTicks;
    private final boolean repeating;
    private final Runnable action;
    private long nextRunTick;

    ScheduledTask(String id, long periodTicks, boolean repeating, long firstRunTick, Runnable action) {
        this.id = id;
        this.periodTicks = periodTicks;
        this.repeating = repeating;
        this.nextRunTick = firstRunTick;
        this.action = action;
    }

    public String id() {
        return id;
    }

    public boolean repeating() {
        return repeating;
    }

    public long periodTicks() {
        return periodTicks;
    }

    long nextRunTick() {
        return nextRunTick;
    }

    boolean isDue(long currentTick) {
        return currentTick >= nextRunTick;
    }

    void reschedule(long currentTick) {
        this.nextRunTick = currentTick + periodTicks;
    }

    void run() {
        action.run();
    }
}
