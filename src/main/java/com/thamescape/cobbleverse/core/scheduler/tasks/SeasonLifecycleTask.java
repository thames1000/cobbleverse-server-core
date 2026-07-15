package com.thamescape.cobbleverse.core.scheduler.tasks;

import com.thamescape.cobbleverse.core.season.SeasonService;

/** Periodically checks for season start/end transitions and fires them once. Runs on the server thread. */
public final class SeasonLifecycleTask implements Runnable {

    public static final String ID = "core:season_lifecycle";

    private final SeasonService seasons;

    public SeasonLifecycleTask(SeasonService seasons) {
        this.seasons = seasons;
    }

    @Override
    public void run() {
        seasons.checkLifecycle();
    }
}
