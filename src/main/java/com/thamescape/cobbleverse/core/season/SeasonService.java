package com.thamescape.cobbleverse.core.season;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.TransactionManager;
import com.thamescape.cobbleverse.core.persistence.repository.SeasonRepository;
import com.thamescape.cobbleverse.core.reward.RewardService;
import com.thamescape.cobbleverse.core.season.objective.ObjectiveRegistry;
import com.thamescape.cobbleverse.core.util.ServerHolder;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns season progress and lifecycle. Objective progress and season points are stored per player;
 * completing an objective awards its points, and crossing a milestone grants a reward through the
 * central {@link RewardService} (inheriting claim dedup and offline queueing).
 *
 * <p>Which season is current is named by {@code core.json}'s {@code activeSeason}; definitions live in
 * {@code seasons.json}. Lifecycle state (upcoming / active / ended) is derived from each season's
 * configured window.
 */
public final class SeasonService {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/SEASON");

    private final ConfigManager config;
    private final DatabaseManager db;
    private final SeasonRepository repository;
    private final RewardService rewards;
    private final AuditService audit;
    private final ObjectiveRegistry objectiveRegistry;

    public SeasonService(ConfigManager config, DatabaseManager db, SeasonRepository repository,
                         RewardService rewards, AuditService audit, ObjectiveRegistry objectiveRegistry) {
        this.config = config;
        this.db = db;
        this.repository = repository;
        this.rewards = rewards;
        this.audit = audit;
        this.objectiveRegistry = objectiveRegistry;
    }

    /** The objective-type registry. Future modules register event-driven objective handlers here. */
    public ObjectiveRegistry objectiveRegistry() {
        return objectiveRegistry;
    }

    // --- Definitions & state ----------------------------------------------------------------------

    /**
     * The season id the server is <b>configured</b> to feature (from {@code core.json}'s
     * {@code activeSeason}). This is the current season by intent — it may not be in the
     * {@link SeasonState#ACTIVE} state (it could be upcoming, ended, or disabled). Use
     * {@link #isConfiguredSeasonActive()} to check the live state.
     */
    public String configuredSeasonId() {
        return config.core().activeSeason;
    }

    public Optional<SeasonDefinition> definition(String seasonId) {
        return seasonId == null || seasonId.isBlank()
                ? Optional.empty()
                : Optional.ofNullable(config.seasons().seasons.get(seasonId));
    }

    /** The definition for the configured season (regardless of whether it is currently ACTIVE). */
    public Optional<SeasonDefinition> configuredSeason() {
        return definition(configuredSeasonId());
    }

    /** True if the configured season exists and is currently in the ACTIVE state. */
    public boolean isConfiguredSeasonActive() {
        return configuredSeason().map(def -> state(def) == SeasonState.ACTIVE).orElse(false);
    }

    public SeasonState state(SeasonDefinition def) {
        if (!def.enabled) {
            return SeasonState.DISABLED;
        }
        Instant now = Instant.now();
        Instant start = parse(def.startsAt);
        Instant end = parse(def.endsAt);
        if (start != null && now.isBefore(start)) {
            return SeasonState.UPCOMING;
        }
        if (end != null && !now.isBefore(end)) {
            return SeasonState.ENDED;
        }
        return SeasonState.ACTIVE;
    }

    // --- Progress ---------------------------------------------------------------------------------

    public int points(UUID uuid, String seasonId) {
        return db.callSync(conn -> repository.points(conn, uuid, seasonId));
    }

    /** Top players by points in a season, highest first. */
    public java.util.List<com.thamescape.cobbleverse.core.persistence.repository.SeasonRepository.LeaderboardEntry>
            leaderboard(String seasonId, int limit) {
        return db.callSync(conn -> repository.topByPoints(conn, seasonId, limit));
    }

    public SeasonProgress progress(UUID uuid, String seasonId) {
        return db.callSync(conn -> {
            int points = repository.points(conn, uuid, seasonId);
            Map<String, ObjectiveProgress> objectives = repository.objectives(conn, uuid, seasonId);
            return new SeasonProgress(seasonId, points, objectives);
        });
    }

    /**
     * Adds season points (which may be negative to correct a mistake, clamped at zero) and grants any
     * milestones newly crossed. The read-and-write is one transaction, so the total is never lost or
     * doubled by a concurrent change. Returns the new total.
     */
    public int addPoints(UUID uuid, String seasonId, int delta, String reason) {
        int[] change = db.callInTransaction(conn -> {
            int old = repository.points(conn, uuid, seasonId);
            int updated = Math.max(0, old + delta);
            repository.setPoints(conn, uuid, seasonId, updated);
            return new int[]{old, updated};
        });
        audit.record(AuditEntry.builder(AuditType.SEASON_POINTS_CHANGED)
                .target(uuid).source(reason).context(seasonId + ": " + change[0] + " -> " + change[1]));
        // addPoints is called from the admin command (server thread), so granting inline is safe.
        definition(seasonId).ifPresent(def -> {
            for (String rewardId : collectCrossedMilestones(def, change[0], change[1])) {
                rewards.grant(uuid, rewardId, "season:" + seasonId);
            }
        });
        return change[1];
    }

    /**
     * Adds progress to an objective. Progress only counts while the season is ACTIVE. Progress is
     * clamped to {@code [0, required]}. On completion, the objective is marked complete <b>and</b> its
     * points are awarded in a single transaction (so a crash can't leave one without the other);
     * milestone rewards are then granted (idempotently). No-op if already complete.
     */
    public ObjectiveResult addObjectiveProgress(UUID uuid, String seasonId, String objectiveId, int amount) {
        Optional<SeasonDefinition> def = definition(seasonId);
        if (def.isEmpty()) {
            return new ObjectiveResult(ObjectiveResult.Status.UNKNOWN_SEASON, 0, false);
        }
        Optional<ObjectiveDefinition> objective = def.get().objective(objectiveId);
        if (objective.isEmpty()) {
            return new ObjectiveResult(ObjectiveResult.Status.UNKNOWN_OBJECTIVE, 0, false);
        }
        if (state(def.get()) != SeasonState.ACTIVE) {
            return new ObjectiveResult(ObjectiveResult.Status.SEASON_NOT_ACTIVE, 0, false);
        }

        ObjectiveTxn txn = db.callInTransaction(conn ->
                applyObjectiveOnConn(conn, uuid, seasonId, objective.get(), amount));
        if (txn.status() == ObjectiveResult.Status.COMPLETED) {
            auditCompletion(uuid, seasonId, objectiveId, txn);
            // Runs on the caller's (server) thread — an admin command — so granting here is safe.
            for (String rewardId : collectCrossedMilestones(def.get(), txn.oldPoints(), txn.newPoints())) {
                rewards.grant(uuid, rewardId, "season:" + seasonId);
            }
        }
        return new ObjectiveResult(txn.status(), txn.progress(), txn.completed());
    }

    /** One matching objective and the progress a game event contributes to it. */
    public record ObjectiveMatch(String objectiveId, int amount) {
    }

    /**
     * Applies progress to several objectives for one player <b>off the server thread</b>: all DB work
     * runs in one job on the database worker, and any milestone reward grants (which may deliver items
     * or run commands) are marshalled back onto the server thread. Callers (game-event listeners)
     * compute matches cheaply and hand off here without blocking the tick.
     */
    public void advanceObjectivesAsync(UUID uuid, String seasonId, List<ObjectiveMatch> matches) {
        if (matches.isEmpty()) {
            return;
        }
        SeasonDefinition def = definition(seasonId).orElse(null);
        if (def == null) {
            return;
        }
        db.supplyAsync(conn -> {
            List<String> milestoneRewards = new ArrayList<>();
            for (ObjectiveMatch match : matches) {
                ObjectiveDefinition objective = def.objective(match.objectiveId()).orElse(null);
                if (objective == null) {
                    continue;
                }
                ObjectiveTxn[] holder = {null};
                TransactionManager.execute(conn, c ->
                        holder[0] = applyObjectiveOnConn(c, uuid, seasonId, objective, match.amount()));
                ObjectiveTxn txn = holder[0];
                if (txn.status() == ObjectiveResult.Status.COMPLETED) {
                    auditCompletion(uuid, seasonId, objective.id, txn);
                    milestoneRewards.addAll(collectCrossedMilestones(def, txn.oldPoints(), txn.newPoints()));
                }
            }
            return milestoneRewards;
        }).thenAccept(rewardIds -> {
            if (rewardIds.isEmpty()) {
                return;
            }
            MinecraftServer server = ServerHolder.get();
            if (server != null) {
                server.execute(() -> rewardIds.forEach(id -> rewards.grant(uuid, id, "season:" + seasonId)));
            }
        }).exceptionally(t -> {
            LOGGER.warn("Async objective progress failed for {} in '{}': {}", uuid, seasonId, t.getMessage());
            return null;
        });
    }

    /** The shared transaction body: read, clamp, write objective, award points on completion. */
    private ObjectiveTxn applyObjectiveOnConn(java.sql.Connection conn, UUID uuid, String seasonId,
                                              ObjectiveDefinition objective, int amount)
            throws java.sql.SQLException {
        ObjectiveProgress current = repository.objective(conn, uuid, seasonId, objective.id).orElse(null);
        if (current != null && current.completed()) {
            return new ObjectiveTxn(ObjectiveResult.Status.ALREADY_COMPLETE, current.progress(), true, -1, -1);
        }
        int prev = current == null ? 0 : current.progress();
        int newProgress = Math.max(0, Math.min(objective.required, prev + amount));
        boolean completed = newProgress >= objective.required;
        repository.setObjective(conn, uuid, seasonId, objective.id, newProgress, completed);
        int oldPoints = -1;
        int newPoints = -1;
        if (completed) {
            oldPoints = repository.points(conn, uuid, seasonId);
            newPoints = Math.max(0, oldPoints + objective.points);
            repository.setPoints(conn, uuid, seasonId, newPoints);
        }
        return new ObjectiveTxn(completed ? ObjectiveResult.Status.COMPLETED
                : ObjectiveResult.Status.PROGRESSED, newProgress, completed, oldPoints, newPoints);
    }

    private void auditCompletion(UUID uuid, String seasonId, String objectiveId, ObjectiveTxn txn) {
        audit.record(AuditEntry.builder(AuditType.SEASON_OBJECTIVE_COMPLETED)
                .target(uuid).source("season:" + seasonId).context(objectiveId));
        audit.record(AuditEntry.builder(AuditType.SEASON_POINTS_CHANGED)
                .target(uuid).source("objective:" + objectiveId)
                .context(seasonId + ": " + txn.oldPoints() + " -> " + txn.newPoints()));
        LOGGER.info("Player {} completed objective '{}' in season '{}'", uuid, objectiveId, seasonId);
    }

    private record ObjectiveTxn(ObjectiveResult.Status status, int progress, boolean completed,
                                int oldPoints, int newPoints) {
    }

    private List<String> collectCrossedMilestones(SeasonDefinition def, int oldPoints, int newPoints) {
        List<String> ids = new ArrayList<>();
        if (newPoints <= oldPoints) {
            return ids;
        }
        for (Milestone milestone : def.milestones) {
            if (milestone.reward != null && oldPoints < milestone.points && milestone.points <= newPoints) {
                ids.add(milestone.reward);
                LOGGER.info("Player reached {} points in '{}'; queuing milestone reward '{}'",
                        milestone.points, def.id, milestone.reward);
            }
        }
        return ids;
    }

    // --- Lifecycle --------------------------------------------------------------------------------

    /** Detects season start/end transitions and fires them once. Called on startup and on a timer. */
    public void checkLifecycle() {
        for (SeasonDefinition def : config.seasons().seasons.values()) {
            SeasonState computed = state(def);
            String previous = db.callSync(conn -> repository.lifecycleState(conn, def.id)).orElse(null);
            if (!computed.name().equals(previous)) {
                db.runSync(conn -> repository.setLifecycleState(conn, def.id, computed.name(),
                        System.currentTimeMillis()));
                if (previous != null) {
                    audit.record(AuditEntry.builder(AuditType.SEASON_CHANGED)
                            .source("lifecycle").context(def.id + ": " + previous + " -> " + computed));
                    LOGGER.info("Season '{}' transitioned {} -> {}", def.id, previous, computed);
                }
            }
        }
    }

    @Nullable
    private static Instant parse(@Nullable String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /** Outcome of adding objective progress. */
    public record ObjectiveResult(Status status, int progress, boolean completed) {
        public enum Status {
            PROGRESSED, COMPLETED, ALREADY_COMPLETE, SEASON_NOT_ACTIVE, UNKNOWN_SEASON, UNKNOWN_OBJECTIVE
        }
    }
}
