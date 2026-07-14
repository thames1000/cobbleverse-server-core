package com.thamescape.cobbleverse.core.integration.luckperms;

import com.thamescape.cobbleverse.core.integration.AbstractModIntegration;

/**
 * Detects LuckPerms. Permission checks themselves go through {@code fabric-permissions-api}, which
 * LuckPerms implements; this integration only surfaces presence for reports and health checks.
 */
public final class LuckPermsIntegration extends AbstractModIntegration {

    public LuckPermsIntegration() {
        super("luckperms", "LuckPerms", "luckperms");
    }
}
