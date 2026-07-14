package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.integration.IntegrationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Prints the human-readable startup banner summarising version, storage, active season and every
 * integration's status. Mirrors the report an operator expects to see once per boot.
 */
public final class StartupReport {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/CORE");

    private final String version;
    private final String storage;
    private final String activeSeason;
    private final boolean apiEnabled;
    private final List<IntegrationReport> integrations;

    public StartupReport(String version,
                         String storage,
                         String activeSeason,
                         boolean apiEnabled,
                         List<IntegrationReport> integrations) {
        this.version = version;
        this.storage = storage;
        this.activeSeason = activeSeason;
        this.apiEnabled = apiEnabled;
        this.integrations = integrations;
    }

    public void print() {
        LOGGER.info("Version {}", version);
        LOGGER.info("Storage: {}", storage);
        LOGGER.info("Active season: {}", activeSeason.isBlank() ? "<none>" : activeSeason);
        for (IntegrationReport r : integrations) {
            String status = r.available() && r.version() != null
                    ? "available (" + r.version() + ")"
                    : r.status().name().toLowerCase();
            LOGGER.info("{}: {}", r.displayName(), status);
        }
        LOGGER.info("API server: {}", apiEnabled ? "enabled" : "disabled");
        LOGGER.info("Loaded successfully");
    }
}
