package com.thamescape.cobbleverse.core.diagnostics;

import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.config.ConfigValidator;

import java.util.List;

/** Verifies the live core configuration is present and still valid. */
public final class ConfigHealthCheck implements HealthCheck {

    private final ConfigManager configManager;

    public ConfigHealthCheck(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String name() {
        return "Configuration";
    }

    @Override
    public HealthCheckResult run() {
        try {
            List<String> problems = ConfigValidator.validate(configManager.core());
            if (problems.isEmpty()) {
                return HealthCheckResult.ok(name());
            }
            return HealthCheckResult.error(name(), problems.size() + " problem(s): " + problems.get(0));
        } catch (Exception e) {
            return HealthCheckResult.error(name(), e.getMessage());
        }
    }
}
