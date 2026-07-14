package com.thamescape.cobbleverse.core.integration;

import com.thamescape.cobbleverse.core.integration.cobbledollars.CobbleDollarsIntegration;
import com.thamescape.cobbleverse.core.integration.cobblemon.CobblemonIntegration;
import com.thamescape.cobbleverse.core.integration.holodisplays.HoloDisplaysIntegration;
import com.thamescape.cobbleverse.core.integration.ledger.LedgerIntegration;
import com.thamescape.cobbleverse.core.integration.luckperms.LuckPermsIntegration;
import com.thamescape.cobbleverse.core.integration.placeholder.PlaceholderApiIntegration;
import com.thamescape.cobbleverse.core.integration.skiescrates.SkiesCratesIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registers all known integrations, detects them once at startup, and exposes their status for the
 * {@code /cvcore integrations} command and health checks. Re-detection is available for reloads.
 */
public final class IntegrationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/INTEGRATION");

    private final List<Integration> integrations = new CopyOnWriteArrayList<>();
    private volatile List<IntegrationReport> reports = List.of();

    /** Registers the built-in integrations. Order here is the order shown in reports. */
    public void registerDefaults() {
        register(new LuckPermsIntegration());
        register(new CobblemonIntegration());
        register(new LedgerIntegration());
        register(new SkiesCratesIntegration());
        register(new CobbleDollarsIntegration());
        register(new HoloDisplaysIntegration());
        register(new PlaceholderApiIntegration());
    }

    public void register(Integration integration) {
        integrations.add(integration);
    }

    /** Detects every registered integration and caches the reports. Never throws. */
    public List<IntegrationReport> detectAll() {
        List<IntegrationReport> results = integrations.stream()
                .map(Integration::detect)
                .toList();
        this.reports = results;
        for (IntegrationReport r : results) {
            if (r.status() == IntegrationStatus.ERROR) {
                LOGGER.warn("Integration {} errored during detection: {}", r.id(), r.detail());
            }
        }
        return results;
    }

    /** The most recent detection reports (empty until {@link #detectAll()} runs). */
    public List<IntegrationReport> reports() {
        return reports;
    }

    public Optional<IntegrationReport> report(String id) {
        return reports.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public boolean isAvailable(String id) {
        return report(id).map(IntegrationReport::available).orElse(false);
    }
}
