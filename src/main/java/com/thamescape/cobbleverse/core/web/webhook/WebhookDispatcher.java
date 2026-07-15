package com.thamescape.cobbleverse.core.web.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Delivers webhook POSTs asynchronously off any game/server thread, with bounded exponential-backoff
 * retries. Delivery is <b>best-effort</b>: a payload that still fails after {@code maxRetries} is
 * dropped (dead-lettered) with a {@code [CV-WEB-001]} warning — a missed notification is not worth a
 * durable outbox the way a lost reward is. All work runs on a single daemon scheduler thread.
 */
public final class WebhookDispatcher implements WebhookSink {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/WEB");
    private static final long MAX_BACKOFF_MS = 30_000L;

    private final HttpClient client;
    private final ScheduledExecutorService scheduler;
    private final Duration timeout;
    private final int maxRetries;

    public WebhookDispatcher(int timeoutSeconds, int maxRetries) {
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = Math.max(0, maxRetries);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cvcore-webhook");
            thread.setDaemon(true);
            return thread;
        });
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Queues a JSON body for delivery to {@code url}. Returns immediately; retries happen in the background. */
    @Override
    public void dispatch(String name, String url, String jsonBody) {
        attempt(name, url, jsonBody, 0);
    }

    private void attempt(String name, String url, String body, int attempt) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (RuntimeException e) {
            LOGGER.warn("[CV-WEB-002] Webhook '{}' has an unusable URL, not delivering: {}", name, e.toString());
            return;
        }
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error == null && response.statusCode() >= 200 && response.statusCode() < 300) {
                        return; // delivered
                    }
                    String reason = error != null ? error.toString() : ("HTTP " + response.statusCode());
                    if (attempt < maxRetries) {
                        long delayMs = backoffMillis(attempt);
                        scheduler.schedule(() -> attempt(name, url, body, attempt + 1),
                                delayMs, TimeUnit.MILLISECONDS);
                    } else {
                        LOGGER.warn("[CV-WEB-001] Webhook '{}' dropped after {} attempt(s): {}",
                                name, attempt + 1, reason);
                    }
                });
    }

    private static long backoffMillis(int attempt) {
        return Math.min(MAX_BACKOFF_MS, 500L * (1L << Math.min(attempt, 16)));
    }

    /** Stops the retry scheduler. In-flight HTTP runs on daemon threads and will not block shutdown. */
    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
