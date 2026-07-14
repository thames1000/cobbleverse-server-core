package com.thamescape.cobbleverse.core.scheduler.tasks;

import com.thamescape.cobbleverse.core.player.PlayerProfileService;

/** Periodically credits elapsed playtime to online players. Runs on the server thread. */
public final class PlaytimeUpdateTask implements Runnable {

    public static final String ID = "core:playtime_update";

    private final PlayerProfileService profiles;

    public PlaytimeUpdateTask(PlayerProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public void run() {
        profiles.accrueAll(System.currentTimeMillis());
    }
}
