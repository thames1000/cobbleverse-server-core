package com.thamescape.cobbleverse.core.api;

/**
 * The developer entry point for mods that extend Cobbleverse Server Core. Implement this and declare it
 * as a Fabric entrypoint under the {@code "cobbleverse"} key in your {@code fabric.mod.json}:
 *
 * <pre>
 * "entrypoints": {
 *   "cobbleverse": [ "com.example.MyCobbleverseExtension" ]
 * }
 * </pre>
 *
 * <p>The core invokes every registered extension <b>once during startup, before configuration is
 * validated</b> — so a custom objective type you register here is recognised by config validation
 * instead of being rejected as unknown. Register your handlers/providers via the supplied
 * {@link CobbleverseRegistrar}; do not do heavy work or touch the world here (the server is still
 * starting). An extension that throws is isolated and logged; it does not abort the server.
 *
 * <p><b>Experimental (0.8.0):</b> this surface may change until it is frozen in 1.0.
 */
@FunctionalInterface
public interface CobbleverseExtension {

    /** The Fabric entrypoint key mods declare their extension under. */
    String ENTRYPOINT_KEY = "cobbleverse";

    /** Register objective handlers, listeners, health checks and currency providers with the core. */
    void registerCobbleverse(CobbleverseRegistrar registrar);
}
