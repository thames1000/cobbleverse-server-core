package com.thamescape.cobbleverse.core.web.webhook;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.config.WebConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Subscription matching and payload selection, exercised through a capturing sink (no network). */
class WebhookServiceTest {

    private static final class CapturingSink implements WebhookSink {
        record Call(String name, String url, String body) {
        }

        final List<Call> calls = new ArrayList<>();

        @Override
        public void dispatch(String name, String url, String jsonBody) {
            calls.add(new Call(name, url, jsonBody));
        }
    }

    private static AuditEntry entry(AuditType type) {
        return AuditEntry.builder(type).source("test").context("ctx").build(Instant.now());
    }

    private static WebConfig.Subscription sub(String name, String format, List<String> events) {
        WebConfig.Subscription s = new WebConfig.Subscription();
        s.name = name;
        s.enabled = true;
        s.url = "https://example.com/" + name;
        s.format = format;
        s.events = events;
        return s;
    }

    @Test
    void onlyMatchingAuditTypesAreForwarded() {
        WebConfig.Webhooks config = new WebConfig.Webhooks();
        config.subscriptions.add(sub("seasons", "generic", List.of("SEASON_CHANGED")));
        CapturingSink sink = new CapturingSink();
        WebhookService service = new WebhookService(config, sink);

        service.onAudit(entry(AuditType.SEASON_CHANGED));
        service.onAudit(entry(AuditType.EVENT_STATE_CHANGED));

        assertEquals(1, sink.calls.size(), "only the subscribed type is delivered");
        assertEquals("seasons", sink.calls.get(0).name());
    }

    @Test
    void wildcardForwardsEveryType() {
        WebConfig.Webhooks config = new WebConfig.Webhooks();
        config.subscriptions.add(sub("all", "generic", List.of("*")));
        CapturingSink sink = new CapturingSink();
        WebhookService service = new WebhookService(config, sink);

        service.onAudit(entry(AuditType.SEASON_CHANGED));
        service.onAudit(entry(AuditType.REWARD_GRANTED));

        assertEquals(2, sink.calls.size(), "\"*\" forwards every audited action");
    }

    @Test
    void disabledSubscriptionIsNotWired() {
        WebConfig.Webhooks config = new WebConfig.Webhooks();
        WebConfig.Subscription disabled = sub("off", "generic", List.of("*"));
        disabled.enabled = false;
        config.subscriptions.add(disabled);
        CapturingSink sink = new CapturingSink();
        WebhookService service = new WebhookService(config, sink);

        assertEquals(0, service.activeSubscriptions(), "a disabled subscription is not active");
        service.onAudit(entry(AuditType.SEASON_CHANGED));
        assertEquals(0, sink.calls.size(), "a disabled subscription delivers nothing");
    }

    @Test
    void formatSelectsThePayloadShape() {
        WebConfig.Webhooks config = new WebConfig.Webhooks();
        config.subscriptions.add(sub("discord", "discord", List.of("*")));
        config.subscriptions.add(sub("generic", "generic", List.of("*")));
        CapturingSink sink = new CapturingSink();
        WebhookService service = new WebhookService(config, sink);

        service.onAudit(entry(AuditType.SEASON_CHANGED));

        String discordBody = sink.calls.stream().filter(c -> c.name().equals("discord"))
                .findFirst().orElseThrow().body();
        String genericBody = sink.calls.stream().filter(c -> c.name().equals("generic"))
                .findFirst().orElseThrow().body();
        assertTrue(discordBody.contains("embeds"), "discord format emits an embed");
        assertTrue(genericBody.contains("\"event\""), "generic format emits a flat event object");
    }
}
