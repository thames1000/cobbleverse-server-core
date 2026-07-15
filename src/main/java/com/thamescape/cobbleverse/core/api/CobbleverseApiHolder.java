package com.thamescape.cobbleverse.core.api;

/**
 * Holds the single live {@link CobbleverseApi} instance behind a volatile reference. The core installs
 * it once startup is complete; mods read it via {@link CobbleverseApi#get()}.
 *
 * <p><b>Internal:</b> {@link #install} is called by the core during bootstrap. Mods should not call it.
 */
public final class CobbleverseApiHolder {

    private static volatile CobbleverseApi instance;

    private CobbleverseApiHolder() {
    }

    /** Publishes the API instance. Called once by the core at the end of startup. */
    public static void install(CobbleverseApi api) {
        instance = api;
    }

    /** True once {@link #install} has run. */
    public static boolean isReady() {
        return instance != null;
    }

    /** The installed instance, or throws if the core has not finished starting. */
    public static CobbleverseApi require() {
        CobbleverseApi api = instance;
        if (api == null) {
            throw new IllegalStateException(
                    "Cobbleverse API accessed before the core finished starting; wait for SERVER_STARTED "
                            + "or use CobbleverseApi.isReady()");
        }
        return api;
    }
}
