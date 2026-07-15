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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of the read-only API over a real loopback {@link ApiServer}: method gating, API-key
 * auth, routing, and error statuses. Data is a fake {@link ApiData} so no database is involved.
 */
class ApiServerTest {

    private static final String KEY = "test-secret";

    /** Canned data: {@code event("missing")} is unknown (404); everything else returns an object. */
    private static final class FakeData implements ApiData {
        @Override
        public JsonObject health() {
            JsonObject o = new JsonObject();
            o.addProperty("status", "OK");
            return o;
        }

        @Override
        public JsonObject season(@Nullable String seasonId) {
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
        public JsonObject stats(UUID uuid) {
            return new JsonObject();
        }
    }

    private ApiServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void start() {
        ApiRouter router = new ApiRouter(new FakeData(), KEY, 100);
        server = new ApiServer("127.0.0.1", 0, router); // 0 = ephemeral port
        server.start();
        assertTrue(server.isRunning(), "the API server must bind on loopback");
        base = "http://127.0.0.1:" + server.boundPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> get(String path, @Nullable String key) throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (key != null) {
            req.header("X-API-Key", key);
        }
        return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsServedWithoutAuth() throws Exception {
        HttpResponse<String> res = get("/health", null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("status"), "health returns a status body");
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
    void missingRequiredParamIs400() throws Exception {
        assertEquals(400, get("/api/v1/event", KEY).statusCode());
    }

    @Test
    void badUuidIs400() throws Exception {
        assertEquals(400, get("/api/v1/player/not-a-uuid", KEY).statusCode());
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
}
