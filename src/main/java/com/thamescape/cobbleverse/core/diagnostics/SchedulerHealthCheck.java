package com.thamescape.cobbleverse.core.diagnostics;

import com.thamescape.cobbleverse.core.scheduler.CoreScheduler;

/** Reports active task count and whether any scheduled task has failed. */
public final class SchedulerHealthCheck implements HealthCheck {

    private final CoreScheduler scheduler;

    public SchedulerHealthCheck(CoreScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "Scheduler";
    }

    @Override
    public HealthCheckResult run() {
        int failed = scheduler.failedRuns();
        int active = scheduler.activeTaskIds().size();
        if (failed > 0) {
            return HealthCheckResult.warn(name(), failed + " failed run(s); " + active + " active");
        }
        return HealthCheckResult.ok(name(), active + " active task(s)");
    }
}
