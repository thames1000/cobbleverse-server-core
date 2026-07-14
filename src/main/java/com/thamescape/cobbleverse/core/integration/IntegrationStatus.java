package com.thamescape.cobbleverse.core.integration;

/** Outcome of detecting a third-party integration. */
public enum IntegrationStatus {
    /** The mod is present and usable. */
    AVAILABLE,
    /** The mod is not installed. */
    UNAVAILABLE,
    /** The mod is installed but disabled by core configuration. */
    DISABLED,
    /** The mod is present but its version is incompatible. */
    INCOMPATIBLE,
    /** Detection or hookup failed unexpectedly. */
    ERROR
}
