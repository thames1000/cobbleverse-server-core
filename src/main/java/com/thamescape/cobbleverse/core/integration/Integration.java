package com.thamescape.cobbleverse.core.integration;

/**
 * A wrapper around a single third-party mod. Feature code talks to integrations, never to the mods
 * directly, so a missing mod degrades gracefully instead of crashing.
 *
 * <p>Contract: an integration must detect whether its mod exists, report its status, and never throw
 * from {@link #detect()} — it returns an {@link IntegrationStatus#ERROR} report instead.
 */
public interface Integration {

    /** Stable short id, e.g. {@code luckperms}. */
    String id();

    /** Human-readable name for reports, e.g. {@code LuckPerms}. */
    String displayName();

    /** Detects presence and compatibility. Must not throw. */
    IntegrationReport detect();
}
