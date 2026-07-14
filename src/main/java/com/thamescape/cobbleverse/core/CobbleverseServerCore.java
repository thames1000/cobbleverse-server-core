package com.thamescape.cobbleverse.core;

import com.thamescape.cobbleverse.core.bootstrap.CoreBootstrap;
import com.thamescape.cobbleverse.core.bootstrap.CoreShutdown;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint. Delegates all initialization ordering to {@link CoreBootstrap} and registers a
 * shutdown hook. Kept intentionally thin so the startup sequence lives in one readable place.
 */
public final class CobbleverseServerCore implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CORE");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}...", CoreConstants.MOD_NAME);
        try {
            CoreBootstrap.run();
        } catch (RuntimeException e) {
            LOGGER.error("Fatal error during startup; {} did not initialize: {}",
                    CoreConstants.MOD_NAME, e.getMessage(), e);
            throw e;
        }

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CoreShutdown.run());
    }
}
