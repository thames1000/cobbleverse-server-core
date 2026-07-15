package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.api.CobbleverseExtension;
import com.thamescape.cobbleverse.core.api.CobbleverseRegistrar;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers and runs {@link CobbleverseExtension}s declared under the {@code "cobbleverse"} Fabric
 * entrypoint. Each extension is isolated: a broken one (registration throwing, or a jar failing to load
 * a class) is logged and skipped rather than aborting the whole server — a bad plugin should not take
 * the core down. Called once during startup, before configuration validation.
 */
final class ExtensionLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/API");

    private ExtensionLoader() {
    }

    /** Discovers extensions and applies them to {@code registrar}. Returns how many were invoked. */
    static int discoverAndApply(CobbleverseRegistrar registrar) {
        List<CobbleverseExtension> extensions = discover();
        apply(extensions, registrar);
        return extensions.size();
    }

    private static List<CobbleverseExtension> discover() {
        List<CobbleverseExtension> extensions = new ArrayList<>();
        for (EntrypointContainer<CobbleverseExtension> container : FabricLoader.getInstance()
                .getEntrypointContainers(CobbleverseExtension.ENTRYPOINT_KEY, CobbleverseExtension.class)) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                extensions.add(container.getEntrypoint());
            } catch (Throwable t) {
                // A broken extension jar (e.g. NoClassDefFoundError from a missing dependency) is a
                // LinkageError, not an Exception — catch Throwable so it can't abort startup.
                LOGGER.error("Could not load cobbleverse extension from mod '{}' (skipped): {}",
                        modId, t.toString());
            }
        }
        return extensions;
    }

    /** Offers the registrar to each extension, isolating failures. Package-visible for testing. */
    static void apply(List<CobbleverseExtension> extensions, CobbleverseRegistrar registrar) {
        for (CobbleverseExtension extension : extensions) {
            try {
                extension.registerCobbleverse(registrar);
            } catch (Throwable t) {
                LOGGER.error("Cobbleverse extension {} failed during registration (skipped): {}",
                        extension.getClass().getName(), t.toString());
            }
        }
    }
}
