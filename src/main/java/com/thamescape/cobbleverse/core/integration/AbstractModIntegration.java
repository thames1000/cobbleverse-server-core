package com.thamescape.cobbleverse.core.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

/**
 * Base integration that detects a mod purely from the Fabric mod list. Subclasses provide the mod id
 * and display name; specialised integrations can override {@link #detect()} to add version checks or
 * wire up listeners once the mod is confirmed present.
 */
public abstract class AbstractModIntegration implements Integration {

    private final String id;
    private final String displayName;
    private final String modId;

    protected AbstractModIntegration(String id, String displayName, String modId) {
        this.id = id;
        this.displayName = displayName;
        this.modId = modId;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /** The Fabric mod id this integration looks for. */
    public String modId() {
        return modId;
    }

    protected boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    protected Optional<String> modVersion() {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(ModContainer::getMetadata)
                .map(meta -> meta.getVersion().getFriendlyString());
    }

    @Override
    public IntegrationReport detect() {
        try {
            if (!isLoaded()) {
                return new IntegrationReport(id, displayName, IntegrationStatus.UNAVAILABLE, null, null);
            }
            String version = modVersion().orElse(null);
            return new IntegrationReport(id, displayName, IntegrationStatus.AVAILABLE, version, null);
        } catch (Exception e) {
            return new IntegrationReport(id, displayName, IntegrationStatus.ERROR, null, e.getMessage());
        }
    }
}
