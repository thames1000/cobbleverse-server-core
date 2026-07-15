package com.thamescape.cobbleverse.core.web.api;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of the read-only API over a real loopback {@link ApiServer}: method gating, API-key
 * auth, routing, rate limiting, concurrency admission, and error statuses. Data is a fake {@link ApiData}.
 */
class ApiServerTest {

    private static final String KEY = "test-secret";
    private static final UUID NIL = new UUID(0, 0);

    /**
     * Canned data: {@code event("missing")} and {@code stats(NIL)} are unknown (404). If given a gate,
     * {@code season()} blocks on it (to occupy a concurrency permit for the 503 test).
     */
    private static final class FakeData implements ApiData {
        @Nullable
        private final CountDownLatch entered;
        @Nullable
        private final CountDownLatch release;

        FakeData() {
            this(null, null);
        }

        FakeData(@Nullable CountDownLatch entered, @Nullable CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public JsonObject health() {
            JsonObject o = new JsonObject();
            o.addProperty("status", "OK");
            o.add("checks", new com.google.gson.JsonArray());
            return o;
        }

        @Override
        public JsonObject season(@Nullable String seasonId) {
            if (entered != null && release != null) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", seasonId == null ? "configured" : seasonId);
            return o;
        }

        @Override
        public JsonObject leaderboard(@Nullable String seasonId, int limit) {
            JsonObject o = new JsonObject();
            o.addProperty("limit", limit);
            return o;
        }

        @Override
        @Nullable
        public JsonObject event(String eventId) {
            return "missing".equals(eventId) ? null : new JsonObject();
        }

        @Override
        public JsonObject player(UUID uuid) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            return o;
        }

        @Override
        @Nullable
        public JsonObject stats(UUID uuid) {
            return uuid.equals(NIL) ? null : new JsonObject();
        }
    }

    private ApiServer server;
    private HttpClient client;
    private String base;

    private static ApiServer serve(ApiData data, int maxConcurrent, int rateLimit) {
        ApiRouter router = new ApiRouter(data, KEY, 100, maxConcurrent, rateLimit);
        ApiServer server = new ApiServer("127.0.0.1", 0, maxConcurrent + 4, router); // 0 = ephemeral port
        server.start();
        return server;
    }

    @BeforeEach
    void start() {
        server = serve(new FakeData(), 6, 0); // rate limiting off for the routing/auth tests
        assertTrue(server.isRunning(), "the API server must bind on loopback");
        base = "http://127.0.0.1:" + server.boundPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> get(String path, @Nullable String key) throws IOException, InterruptedException {
        return get(base, path, key);
    }

    private HttpResponse<String> get(String origin, String path, @Nullable String key)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(origin + path)).GET();
        if (key != null) {
            req.header("X-API-Key", key);
        }
        return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void livenessHealthIsServedWithoutAuthAndIsMinimal() throws Exception {
        HttpResponse<String> res = get("/health", null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("status"), "liveness returns a status");
        assertTrue(!res.body().contains("checks"), "public liveness must not leak detailed checks");
    }

    @Test
    void detailedHealthRequiresAuth() throws Exception {
        assertEquals(401, get("/api/v1/health", null).statusCode());
        assertEquals(200, get("/api/v1/health", KEY).statusCode());
    }

    @Test
    void protectedRouteRequiresKey() throws Exception {
        assertEquals(401, get("/api/v1/season", null).statusCode());
    }

    @Test
    void keyViaHeaderIsAccepted() throws Exception {
        assertEquals(200, get("/api/v1/season", KEY).statusCode());
    }

    @Test
    void bearerTokenIsAccepted() throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/season"))
                .header("Authorization", "Bearer " + KEY).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        assertEquals(401, get("/api/v1/season", "wrong").statusCode());
    }

    @Test
    void unknownEventIs404() throws Exception {
        assertEquals(404, get("/api/v1/event?id=missing", KEY).statusCode());
    }

    @Test
    void unknownStatsIs404() throws Exception {
        assertEquals(404, get("/api/v1/stats/" + NIL, KEY).statusCode());
    }

    @Test
    void missingRequiredParamIs400() throws Exception {
        assertEquals(400, get("/api/v1/event", KEY).statusCode());
    }

    @Test
    void badUuidIs400() throws Exception {
        assertEquals(400, get("/api/v1/player/not-a-uuid", KEY).statusCode());
    }

    @Test
    void malformedQueryEncodingIs400() throws Exception {
        // A real client can send a malformed %-escape that java.net.URI (client-side) would reject, so
        // this goes over a raw socket to reach the server's own query decoding.
        assertEquals(400, rawGetStatus("/api/v1/season?id=%ZZ"));
    }

    private int rawGetStatus(String target) throws IOException {
        URI origin = URI.create(base);
        try (java.net.Socket socket = new java.net.Socket(origin.getHost(), origin.getPort())) {
            String request = "GET " + target + " HTTP/1.1\r\n"
                    + "Host: " + origin.getHost() + ":" + origin.getPort() + "\r\n"
                    + "X-API-Key: " + KEY + "\r\n"
                    + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
            String statusLine = reader.readLine(); // e.g. "HTTP/1.1 400 Bad Request"
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    @Test
    void validUuidIs200() throws Exception {
        assertEquals(200, get("/api/v1/player/" + UUID.randomUUID(), KEY).statusCode());
    }

    @Test
    void unknownRouteIs404() throws Exception {
        assertEquals(404, get("/api/v1/nope", KEY).statusCode());
    }

    @Test
    void nonGetIsRejected() throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(base + "/health"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, res.statusCode());
    }

    @Test
    void rateLimitReturns429() throws Exception {
        ApiServer limited = serve(new FakeData(), 6, 1); // 1 request/minute per client
        try {
            String origin = "http://127.0.0.1:" + limited.boundPort();
            assertEquals(200, get(origin, "/api/v1/season", KEY).statusCode(), "first request is allowed");
            assertEquals(429, get(origin, "/api/v1/season", KEY).statusCode(), "second is over the limit");
        } finally {
            limited.stop();
        }
    }

    @Test
    void concurrencyLimitReturns503() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ApiServer gated = serve(new FakeData(entered, release), 1, 0); // one permit, held by the blocked request
        try {
            String origin = "http://127.0.0.1:" + gated.boundPort();
            AtomicReference<Integer> firstStatus = new AtomicReference<>();
            Thread first = new Thread(() -> {
                try {
                    firstStatus.set(get(origin, "/api/v1/season", KEY).statusCode());
                } catch (Exception e) {
                    firstStatus.set(-1);
                }
            });
            first.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS), "the first request must occupy the permit");

            assertEquals(503, get(origin, "/api/v1/season", KEY).statusCode(),
                    "a second request over the concurrency cap is rejected with 503");

            release.countDown();
            first.join(5000);
            assertEquals(200, firstStatus.get(), "the first (permit-holding) request still succeeds");
        } finally {
            release.countDown();
            gated.stop();
        }
    }
}
