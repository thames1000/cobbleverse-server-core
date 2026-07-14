package com.thamescape.cobbleverse.core.integration;

import org.jetbrains.annotations.Nullable;

/** Immutable result of detecting one integration. */
public record IntegrationReport(
        String id,
        String displayName,
        IntegrationStatus status,
        @Nullable String version,
        @Nullable String detail
) {

    public boolean available() {
        return status == IntegrationStatus.AVAILABLE;
    }
}
