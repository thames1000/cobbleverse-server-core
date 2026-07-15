package com.thamescape.cobbleverse.core.web.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Routes read-only API requests: enforces {@code GET}, checks the API key (every route except
 * {@code /health}), dispatches by path to {@link ApiData}, and writes a JSON response. All failure
 * modes return a JSON {@code {"error": ...}} body with the right status; nothing throws to the server.
 */
public final class ApiRouter implements HttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/WEB");
    private static final Gson GSON = new Gson();

    private final ApiData data;
    private final byte[] apiKey;
    private final int leaderboardMaxLimit;

    public ApiRouter(ApiData data, String apiKey, int leaderboardMaxLimit) {
        this.data = data;
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.leaderboardMaxLimit = leaderboardMaxLimit;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                error(exchange, 405, "method not allowed (read-only API)");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/health".equals(path)) {
                send(exchange, 200, data.health());
                return;
            }
            if (!authorized(exchange)) {
                error(exchange, 401, "missing or invalid API key");
                return;
            }
            route(exchange, path);
        } catch (BadRequestException e) {
            error(exchange, 400, e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.warn("API request failed for {}: {}", exchange.getRequestURI(), e.toString());
            error(exchange, 500, "internal error");
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange, String path) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        switch (path) {
            case "/api/v1/season" -> respond(exchange, data.season(query.get("id")));
            case "/api/v1/leaderboard" ->
                    respond(exchange, data.leaderboard(query.get("season"), clampLimit(query.get("limit"))));
            case "/api/v1/event" -> respond(exchange, data.event(requireParam(query, "id")));
            default -> {
                String uuidPath = matchPrefix(path, "/api/v1/player/");
                if (uuidPath != null) {
                    respond(exchange, data.player(parseUuid(uuidPath)));
                    return;
                }
                String statsPath = matchPrefix(path, "/api/v1/stats/");
                if (statsPath != null) {
                    respond(exchange, data.stats(parseUuid(statsPath)));
                    return;
                }
                error(exchange, 404, "unknown route");
            }
        }
    }

    private void respond(HttpExchange exchange, @org.jetbrains.annotations.Nullable JsonObject body)
            throws IOException {
        if (body == null) {
            error(exchange, 404, "not found");
        } else {
            send(exchange, 200, body);
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (header == null) {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                header = auth.substring(7);
            }
        }
        if (header == null) {
            return false;
        }
        return MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8), apiKey);
    }

    private int clampLimit(@org.jetbrains.annotations.Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Math.min(10, leaderboardMaxLimit);
        }
        int requested;
        try {
            requested = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("limit must be an integer");
        }
        if (requested < 1) {
            throw new BadRequestException("limit must be positive");
        }
        return Math.min(requested, leaderboardMaxLimit);
    }

    private static String requireParam(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("missing required query parameter '" + key + "'");
        }
        return value;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("invalid uuid '" + raw + "'");
        }
    }

    @org.jetbrains.annotations.Nullable
    private static String matchPrefix(String path, String prefix) {
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            return path.substring(prefix.length());
        }
        return null;
    }

    private static Map<String, String> parseQuery(@org.jetbrains.annotations.Nullable String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.putIfAbsent(key, value);
            }
        }
        return params;
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        send(exchange, status, body);
    }

    private static void send(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** A client error carrying the 400 message. */
    private static final class BadRequestException extends RuntimeException {
        BadRequestException(String message) {
            super(message);
        }
    }
}
