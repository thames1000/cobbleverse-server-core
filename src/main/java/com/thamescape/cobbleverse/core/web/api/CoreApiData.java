package com.thamescape.cobbleverse.core.web.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckResult;
import com.thamescape.cobbleverse.core.diagnostics.HealthCheckService;
import com.thamescape.cobbleverse.core.diagnostics.HealthStatus;
import com.thamescape.cobbleverse.core.event.EventDefinition;
import com.thamescape.cobbleverse.core.event.EventParticipant;
import com.thamescape.cobbleverse.core.event.EventService;
import com.thamescape.cobbleverse.core.player.PlayerProfile;
import com.thamescape.cobbleverse.core.player.PlayerProfileService;
import com.thamescape.cobbleverse.core.season.ObjectiveProgress;
import com.thamescape.cobbleverse.core.season.SeasonDefinition;
import com.thamescape.cobbleverse.core.season.SeasonProgress;
import com.thamescape.cobbleverse.core.season.SeasonService;
import com.thamescape.cobbleverse.core.statistics.StatisticsService;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Production {@link ApiData} backed by the core services. Reads only; every call is replay-safe. */
public final class CoreApiData implements ApiData {

    private final Supplier<String> version;
    private final HealthCheckService health;
    private final SeasonService seasons;
    private final EventService events;
    private final StatisticsService statistics;
    private final PlayerProfileService players;

    public CoreApiData(Supplier<String> version, HealthCheckService health, SeasonService seasons,
                       EventService events, StatisticsService statistics, PlayerProfileService players) {
        this.version = version;
        this.health = health;
        this.seasons = seasons;
        this.events = events;
        this.statistics = statistics;
        this.players = players;
    }

    @Override
    public JsonObject health() {
        List<HealthCheckResult> results = health.runAll();
        HealthStatus overall = health.overall(results);
        JsonObject json = new JsonObject();
        json.addProperty("status", overall.name());
        json.addProperty("version", version.get());
        JsonArray checks = new JsonArray();
        for (HealthCheckResult result : results) {
            JsonObject check = new JsonObject();
            check.addProperty("name", result.name());
            check.addProperty("status", result.status().name());
            check.addProperty("detail", result.detail());
            checks.add(check);
        }
        json.add("checks", checks);
        return json;
    }

    @Override
    @Nullable
    public JsonObject season(@Nullable String seasonId) {
        String id = resolveSeasonId(seasonId);
        Optional<SeasonDefinition> def = seasons.definition(id);
        if (def.isEmpty()) {
            return null;
        }
        return seasonJson(id, def.get());
    }

    @Override
    @Nullable
    public JsonObject leaderboard(@Nullable String seasonId, int limit) {
        String id = resolveSeasonId(seasonId);
        if (seasons.definition(id).isEmpty()) {
            return null;
        }
        JsonArray entries = new JsonArray();
        int rank = 1;
        for (var entry : seasons.leaderboard(id, limit)) {
            JsonObject row = new JsonObject();
            row.addProperty("rank", rank++);
            row.addProperty("uuid", entry.uuid().toString());
            row.addProperty("name", entry.label());
            row.addProperty("points", entry.points());
            entries.add(row);
        }
        JsonObject json = new JsonObject();
        json.addProperty("season", id);
        json.add("entries", entries);
        return json;
    }

    @Override
    @Nullable
    public JsonObject event(String eventId) {
        Optional<EventDefinition> def = events.definition(eventId);
        if (def.isEmpty()) {
            return null;
        }
        JsonObject json = new JsonObject();
        json.addProperty("id", eventId);
        json.addProperty("displayName", def.get().displayNameOrId());
        json.addProperty("state", events.state(eventId).name());
        json.addProperty("participants", events.participantCount(eventId));
        JsonArray standings = new JsonArray();
        int rank = 1;
        for (EventParticipant participant : events.leaderboard(eventId, 100)) {
            JsonObject row = new JsonObject();
            row.addProperty("rank", rank++);
            row.addProperty("uuid", participant.uuid().toString());
            row.addProperty("name", participant.label());
            row.addProperty("score", participant.score());
            standings.add(row);
        }
        json.add("standings", standings);
        return json;
    }

    @Override
    @Nullable
    public JsonObject player(UUID uuid) {
        Optional<PlayerProfile> profile = players.find(uuid);
        if (profile.isEmpty()) {
            return null;
        }
        PlayerProfile p = profile.get();
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        json.addProperty("name", p.lastKnownName());
        json.addProperty("firstJoinedAt", p.firstJoinedAt());
        json.addProperty("lastJoinedAt", p.lastJoinedAt());
        json.addProperty("playtimeSeconds", p.playtimeSeconds());
        json.add("season", playerSeasonJson(uuid));
        json.add("stats", statsObject(uuid));
        return json;
    }

    @Override
    public JsonObject stats(UUID uuid) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        json.add("stats", statsObject(uuid));
        return json;
    }

    private JsonObject seasonJson(String id, SeasonDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("displayName", def.displayNameOrId());
        json.addProperty("state", seasons.state(def).name());
        json.addProperty("enabled", def.enabled);
        json.addProperty("startsAt", def.startsAt);
        json.addProperty("endsAt", def.endsAt);
        json.addProperty("objectives", def.objectives.size());
        json.addProperty("milestones", def.milestones.size());
        return json;
    }

    private JsonObject playerSeasonJson(UUID uuid) {
        String id = seasons.configuredSeasonId();
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        if (seasons.definition(id).isEmpty()) {
            return json;
        }
        SeasonProgress progress = seasons.progress(uuid, id);
        json.addProperty("points", progress.points());
        JsonArray objectives = new JsonArray();
        for (Map.Entry<String, ObjectiveProgress> entry : progress.objectives().entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", entry.getKey());
            o.addProperty("progress", entry.getValue().progress());
            o.addProperty("completed", entry.getValue().completed());
            objectives.add(o);
        }
        json.add("objectives", objectives);
        return json;
    }

    private JsonObject statsObject(UUID uuid) {
        Map<String, Long> values = statistics.all(uuid);
        JsonObject json = new JsonObject();
        json.addProperty(StatisticsService.CAPTURES, values.getOrDefault(StatisticsService.CAPTURES, 0L));
        json.addProperty(StatisticsService.SHINIES, values.getOrDefault(StatisticsService.SHINIES, 0L));
        json.addProperty(StatisticsService.BATTLES_WON, values.getOrDefault(StatisticsService.BATTLES_WON, 0L));
        json.addProperty(StatisticsService.SESSIONS, values.getOrDefault(StatisticsService.SESSIONS, 0L));
        return json;
    }

    private String resolveSeasonId(@Nullable String seasonId) {
        return seasonId == null || seasonId.isBlank() ? seasons.configuredSeasonId() : seasonId;
    }
}
