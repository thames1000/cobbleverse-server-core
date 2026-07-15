package com.thamescape.cobbleverse.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Web-integration configuration, persisted as {@code config/cobbleverse-server-core/web.json}.
 *
 * <p>Two independent capabilities, both <b>disabled by default</b>:
 * <ul>
 *   <li>{@link Api} — a read-only HTTP JSON API a web dashboard can poll (leaderboards, season/event
 *       state, player stats, health). Binds to loopback by default and requires an API key.</li>
 *   <li>{@link Webhooks} — outbound HTTP POSTs to configured URLs when selected audited actions occur
 *       (season changes, event completion, milestones, …). Push-only; no inbound port.</li>
 * </ul>
 *
 * <p>Like {@code database.json}, {@code web.json} is fixed at startup and <b>not</b> runtime-reloadable
 * — the HTTP server binds a port and the webhook subscriptions attach to the audit stream once, so
 * changing them takes a restart.
 */
public class WebConfig {

    public int configVersion = CURRENT_VERSION;

    public Api api = new Api();
    public Webhooks webhooks = new Webhooks();

    public static final int CURRENT_VERSION = 1;

    /** Read-only HTTP JSON API settings. */
    public static class Api {
        /** Master switch. When false, no port is bound. */
        public boolean enabled = false;
        /** Interface to bind. Defaults to loopback so the API is not exposed off-box without intent. */
        public String bindAddress = "127.0.0.1";
        public int port = 7070;
        /** Bearer / {@code X-API-Key} secret. Must be non-blank when {@link #enabled}. */
        public String apiKey = "";
        /** Upper bound clamp for leaderboard {@code limit} query params. */
        public int leaderboardMaxLimit = 100;
        /**
         * Max requests processed concurrently. Excess requests get {@code 503} immediately rather than
         * queueing onto — and starving — the shared database worker. Keep this well below the number of
         * gameplay-critical operations you can tolerate delaying.
         */
        public int maxConcurrentRequests = 6;
        /** Per-client-IP request cap per minute (fixed window). {@code 0} disables rate limiting. */
        public int rateLimitPerMinute = 120;
    }

    /** Outbound webhook settings. */
    public static class Webhooks {
        /** Master switch. When false, no subscriptions attach to the audit stream. */
        public boolean enabled = false;
        /** Per-request timeout for a webhook POST. */
        public int timeoutSeconds = 10;
        /** How many extra attempts after the first failure before a delivery is dropped (dead-lettered). */
        public int maxRetries = 3;
        public List<Subscription> subscriptions = new ArrayList<>();
    }

    /** One webhook endpoint and the audited actions it should receive. */
    public static class Subscription {
        public String name = "";
        public boolean enabled = true;
        public String url = "";
        /** Payload shape: {@code generic} (flat JSON) or {@code discord} (embed). */
        public String format = "generic";
        /**
         * Audit type names this endpoint receives (e.g. {@code SEASON_CHANGED}), or a single
         * {@code "*"} to receive every audited action.
         */
        public List<String> events = new ArrayList<>();
    }

    public static WebConfig defaults() {
        WebConfig config = new WebConfig();
        // A disabled sample subscription so the file documents its own shape on first run.
        Subscription sample = new Subscription();
        sample.name = "discord-example";
        sample.enabled = false;
        sample.url = "https://discord.com/api/webhooks/000000000000000000/replace-me";
        sample.format = "discord";
        sample.events = List.of("SEASON_CHANGED", "EVENT_STATE_CHANGED", "SEASON_OBJECTIVE_COMPLETED");
        config.webhooks.subscriptions.add(sample);
        return config;
    }
}
