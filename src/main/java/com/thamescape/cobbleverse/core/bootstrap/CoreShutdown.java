package com.thamescape.cobbleverse.core.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shuts core systems down safely on server stop. In 0.1.0 there is nothing to flush; from 0.2.0 this
 * closes the database, flushes pending profile writes and cancels scheduled tasks.
 */
public final class CoreShutdown {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CORE");

    private CoreShutdown() {
    }

    public static void run() {
        LOGGER.info("Shutting down (no persistent state to flush in 0.1.0)");
        // Future: close DatabaseService, flush PlayerProfileService, cancel scheduler tasks.
    }
}
