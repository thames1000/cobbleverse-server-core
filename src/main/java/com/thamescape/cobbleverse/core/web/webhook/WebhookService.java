package com.thamescape.cobbleverse.core.web.webhook;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.config.WebConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Forwards selected audited actions to external HTTP endpoints. Registered as an
 * {@link com.thamescape.cobbleverse.core.audit.AuditService} listener at startup; each recorded entry
 * is matched against the enabled subscriptions and, on a hit, formatted and handed to the
 * {@link WebhookDispatcher} for async delivery. {@link #onAudit} never blocks the recording thread.
 *
 * <p>Because it consumes the audit stream, webhooks require auditing to be enabled ({@code core.json}
 * {@code enableAuditLog}); an audited action is the trigger for a webhook.
 */
public final class WebhookService {

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final WebhookSink sink;

    public WebhookService(WebConfig.Webhooks config, WebhookSink sink) {
        this.sink = sink;
        for (WebConfig.Subscription sub : config.subscriptions) {
            if (!sub.enabled) {
                continue;
            }
            boolean all = sub.events.contains("*");
            Set<String> types = all ? Set.of() : new HashSet<>(sub.events);
            boolean discord = "discord".equalsIgnoreCase(sub.format);
            subscriptions.add(new Subscription(sub.name, sub.url, discord, all, types));
        }
    }

    /** Audit-stream hook: forwards the entry to every subscription that selected its type. */
    public void onAudit(AuditEntry entry) {
        if (subscriptions.isEmpty()) {
            return;
        }
        String type = entry.action().name();
        for (Subscription sub : subscriptions) {
            if (sub.matches(type)) {
                String body = sub.discord ? WebhookPayloads.discord(entry) : WebhookPayloads.generic(entry);
                sink.dispatch(sub.name, sub.url, body);
            }
        }
    }

    /** Number of enabled subscriptions actually wired (for {@code /cvcore} status). */
    public int activeSubscriptions() {
        return subscriptions.size();
    }

    public void close() {
        sink.close();
    }

    private record Subscription(String name, String url, boolean discord, boolean all, Set<String> types) {
        boolean matches(String auditTypeName) {
            return all || types.contains(auditTypeName.toUpperCase(Locale.ROOT));
        }
    }
}
