package com.thamescape.cobbleverse.core.web.webhook;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thamescape.cobbleverse.core.audit.AuditEntry;

/**
 * Builds webhook request bodies from an {@link AuditEntry}. Two shapes: {@code generic} (a flat JSON
 * object other services can consume) and {@code discord} (an embed the Discord webhook API renders).
 * Pure functions, so they are unit-tested without any network.
 */
public final class WebhookPayloads {

    private static final Gson GSON = new Gson();
    private static final int DISCORD_GREEN = 3_066_993;
    private static final int DISCORD_RED = 15_158_332;

    private WebhookPayloads() {
    }

    /** A flat JSON object describing the audited action. */
    public static String generic(AuditEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("event", entry.action().name());
        json.addProperty("timestamp", entry.timestamp().toString());
        json.addProperty("source", entry.source());
        json.addProperty("actor", actorLabel(entry));
        addIfPresent(json, "target", entry.target() == null ? null : entry.target().toString());
        addIfPresent(json, "context", entry.context());
        json.addProperty("success", entry.success());
        addIfPresent(json, "failureReason", entry.failureReason());
        return GSON.toJson(json);
    }

    /** A single-embed Discord webhook body. */
    public static String discord(AuditEntry entry) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", entry.action().name());
        String description = entry.context() != null ? entry.context() : entry.source();
        embed.addProperty("description", description);
        embed.addProperty("color", entry.success() ? DISCORD_GREEN : DISCORD_RED);
        embed.addProperty("timestamp", entry.timestamp().toString());

        JsonArray fields = new JsonArray();
        fields.add(field("Source", entry.source(), true));
        fields.add(field("Actor", actorLabel(entry), true));
        if (entry.target() != null) {
            fields.add(field("Target", entry.target().toString(), false));
        }
        if (!entry.success() && entry.failureReason() != null) {
            fields.add(field("Failure", entry.failureReason(), false));
        }
        embed.add("fields", fields);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject body = new JsonObject();
        body.add("embeds", embeds);
        return GSON.toJson(body);
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isBlank() ? "-" : value);
        field.addProperty("inline", inline);
        return field;
    }

    private static String actorLabel(AuditEntry entry) {
        if (entry.actorName() != null) {
            return entry.actorName();
        }
        return entry.actor() != null ? entry.actor().toString() : "console";
    }

    private static void addIfPresent(JsonObject json, String key, String value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }
}
