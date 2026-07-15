package com.thamescape.cobbleverse.core.web.webhook;

/**
 * Where {@link WebhookService} hands a formatted payload for delivery. Abstracting the transport keeps
 * the matching/formatting logic unit-testable with a capturing fake, and the production
 * {@link WebhookDispatcher} implements it over HTTP.
 */
public interface WebhookSink {

    /** Queues a JSON body for delivery to {@code url}. Must not block the caller. */
    void dispatch(String name, String url, String jsonBody);

    /** Releases any delivery resources. No-op by default. */
    default void close() {
    }
}
