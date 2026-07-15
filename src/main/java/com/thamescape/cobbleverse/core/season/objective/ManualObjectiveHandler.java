package com.thamescape.cobbleverse.core.season.objective;

/** The manual objective type: progress is set by admins or other modules, never auto-tracked. */
public final class ManualObjectiveHandler implements ObjectiveHandler {

    @Override
    public String type() {
        return ObjectiveType.MANUAL.id();
    }

    @Override
    public boolean manual() {
        return true;
    }
}
