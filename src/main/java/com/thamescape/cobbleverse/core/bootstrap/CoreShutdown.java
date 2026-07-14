package com.thamescape.cobbleverse.core.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shuts core systems down safely on server stop: flushes all cached player profiles synchronously,
 * then closes the database (which drains any remaining queued writes).
 */
public final class CoreShutdown {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CORE");

    private CoreShutdown() {
    }

    public static void run() {
        if (!CoreServices.isReady()) {
            return;
        }
        LOGGER.info("Shutting down...");
        try {
            CoreServices.players().flushAllBlocking(System.currentTimeMillis());
        } catch (Exception e) {
            LOGGER.warn("Error flushing profiles on shutdown: {}", e.getMessage());
        }
        CoreServices.database().close();
    }
}
