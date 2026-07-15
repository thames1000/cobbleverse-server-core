package com.thamescape.cobbleverse.core.web;

import com.thamescape.cobbleverse.core.config.WebConfig;
import com.thamescape.cobbleverse.core.web.api.ApiServer;
import com.thamescape.cobbleverse.core.web.webhook.WebhookService;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the web-integration lifecycle: the read-only {@link ApiServer} and the outbound
 * {@link WebhookService}. Either may be absent (its config section disabled). The API binds on
 * {@link #start()} (server-started) and unbinds on {@link #stop()} (server-stopping); the webhook
 * service is already attached to the audit stream and just needs closing on stop.
 */
public final class WebService {

    private final WebConfig.Api apiConfig;
    @Nullable
    private final ApiServer apiServer;
    @Nullable
    private final WebhookService webhookService;

    public WebService(WebConfig config, @Nullable ApiServer apiServer,
                      @Nullable WebhookService webhookService) {
        this.apiConfig = config.api;
        this.apiServer = apiServer;
        this.webhookService = webhookService;
    }

    /** Binds the API server (if enabled). Called on server start. */
    public void start() {
        if (apiServer != null) {
            apiServer.start();
        }
    }

    /** Unbinds the API server and closes the webhook dispatcher. Called on server stop. */
    public void stop() {
        if (apiServer != null) {
            apiServer.stop();
        }
        if (webhookService != null) {
            webhookService.close();
        }
    }

    public boolean apiEnabled() {
        return apiServer != null;
    }

    public boolean apiRunning() {
        return apiServer != null && apiServer.isRunning();
    }

    /** The configured bind address (for status output). */
    public String apiBindAddress() {
        return apiConfig.bindAddress;
    }

    /** The bound port once running, else the configured port. */
    public int apiPort() {
        if (apiServer != null && apiServer.isRunning()) {
            return apiServer.boundPort();
        }
        return apiConfig.port;
    }

    public boolean webhooksEnabled() {
        return webhookService != null;
    }

    public int activeWebhooks() {
        return webhookService == null ? 0 : webhookService.activeSubscriptions();
    }
}
