package com.thamescape.cobbleverse.core.scheduler.tasks;

import com.thamescape.cobbleverse.core.player.PlayerProfileService;

/** Periodically write-behinds dirty profiles to the database (async). Runs on the server thread. */
public final class DatabaseFlushTask implements Runnable {

    public static final String ID = "core:profile_flush";

    private final PlayerProfileService profiles;

    public DatabaseFlushTask(PlayerProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public void run() {
        profiles.flushDirty();
    }
}
