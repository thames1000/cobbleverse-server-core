package com.thamescape.cobbleverse.core.bootstrap;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that hard dependencies are present before the core initializes. Fabric already enforces
 * the {@code depends} block in {@code fabric.mod.json}; this is a defensive second check that fails
 * with a clear message rather than a later {@code NoClassDefFoundError}.
 */
public final class DependencyValidator {

    /** Mod ids that must be present for the core to function. */
    private static final List<String> REQUIRED_MODS = List.of("fabric-api");

    private DependencyValidator() {
    }

    /** Returns a list of missing required dependencies (empty if all present). */
    public static List<String> validate() {
        List<String> missing = new ArrayList<>();
        FabricLoader loader = FabricLoader.getInstance();
        for (String modId : REQUIRED_MODS) {
            if (!loader.isModLoaded(modId)) {
                missing.add(modId);
            }
        }
        return missing;
    }
}
