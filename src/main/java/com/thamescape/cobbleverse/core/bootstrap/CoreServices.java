package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.integration.IntegrationManager;
import com.thamescape.cobbleverse.core.message.MessageService;
import com.thamescape.cobbleverse.core.permission.PermissionService;

/**
 * Static entry point to the {@link ServiceRegistry}. Convenient for commands and, later, feature
 * modules: {@code CoreServices.messages()}, {@code CoreServices.integrations()}, ...
 *
 * <p>Access before {@link #install(ServiceRegistry)} throws, which surfaces ordering bugs loudly
 * instead of returning null.
 */
public final class CoreServices {

    private static volatile ServiceRegistry registry;

    private CoreServices() {
    }

    static void install(ServiceRegistry serviceRegistry) {
        registry = serviceRegistry;
    }

    public static boolean isReady() {
        return registry != null;
    }

    public static ServiceRegistry registry() {
        ServiceRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException("CoreServices accessed before bootstrap completed");
        }
        return current;
    }

    public static ConfigManager config() {
        return registry().config();
    }

    public static PermissionService permissions() {
        return registry().permissions();
    }

    public static MessageService messages() {
        return registry().messages();
    }

    public static IntegrationManager integrations() {
        return registry().integrations();
    }

    public static AuditService audit() {
        return registry().audit();
    }

    public static HealthCheckService health() {
        return registry().health();
    }
}
