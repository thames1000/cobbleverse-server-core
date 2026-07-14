package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.integration.IntegrationManager;
import com.thamescape.cobbleverse.core.message.MessageService;
import com.thamescape.cobbleverse.core.permission.PermissionService;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.player.PlayerProfileService;
import com.thamescape.cobbleverse.core.scheduler.CoreScheduler;

/**
 * Holds the single instance of each core service so features resolve dependencies through one place
 * rather than constructing their own. Each service is still an ordinary object and independently
 * testable; this is a controlled locator, not ambient global state scattered across the codebase.
 *
 * <p>Populated once by {@link CoreBootstrap} and then read-only.
 */
public final class ServiceRegistry {

    private final ConfigManager configManager;
    private final PermissionService permissionService;
    private final MessageService messageService;
    private final IntegrationManager integrationManager;
    private final AuditService auditService;
    private final HealthCheckService healthCheckService;
    private final DatabaseManager databaseManager;
    private final PlayerProfileService playerProfileService;
    private final CoreScheduler scheduler;

    public ServiceRegistry(ConfigManager configManager,
                           PermissionService permissionService,
                           MessageService messageService,
                           IntegrationManager integrationManager,
                           AuditService auditService,
                           HealthCheckService healthCheckService,
                           DatabaseManager databaseManager,
                           PlayerProfileService playerProfileService,
                           CoreScheduler scheduler) {
        this.configManager = configManager;
        this.permissionService = permissionService;
        this.messageService = messageService;
        this.integrationManager = integrationManager;
        this.auditService = auditService;
        this.healthCheckService = healthCheckService;
        this.databaseManager = databaseManager;
        this.playerProfileService = playerProfileService;
        this.scheduler = scheduler;
    }

    public ConfigManager config() {
        return configManager;
    }

    public PermissionService permissions() {
        return permissionService;
    }

    public MessageService messages() {
        return messageService;
    }

    public IntegrationManager integrations() {
        return integrationManager;
    }

    public AuditService audit() {
        return auditService;
    }

    public HealthCheckService health() {
        return healthCheckService;
    }

    public DatabaseManager database() {
        return databaseManager;
    }

    public PlayerProfileService players() {
        return playerProfileService;
    }

    public CoreScheduler scheduler() {
        return scheduler;
    }
}
