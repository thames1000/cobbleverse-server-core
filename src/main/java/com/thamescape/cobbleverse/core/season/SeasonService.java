package com.thamescape.cobbleverse.core.season;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.TransactionManager;
import com.thamescape.cobbleverse.core.persistence.repository.SeasonRepository;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import com.thamescape.cobbleverse.core.reward.RewardService;
import com.thamescape.cobbleverse.core.reward.RewardStatus;
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
     * Adds season points (may be negative to correct a mistake, clamped at zero). The points change
     * and any newly-crossed milestone's <b>pending reward record</b> are written in one transaction;
     * the reward is then delivered from the durable outbox. Called from the admin command (server
     * thread), so delivery is inline. Returns the new total.
     */
    public int addPoints(UUID uuid, String seasonId, int delta, String reason) {
        long now = System.currentTimeMillis();
        SeasonDefinition def = definition(seasonId).orElse(null);
        int[] change = db.callInTransaction(conn -> {
            int old = repository.points(conn, uuid, seasonId);
            int updated = Math.max(0, old + delta);
            repository.setPoints(conn, uuid, seasonId, updated);
            recordCrossedMilestones(conn, uuid, seasonId, def, old, updated, now);
            return new int[]{old, updated};
        });
        audit.record(AuditEntry.builder(AuditType.SEASON_POINTS_CHANGED)
                .target(uuid).source(reason).context(seasonId + ": " + change[0] + " -> " + change[1]));
        deliverPendingMilestones(uuid);
        return change[1];
    }

    /**
     * Adds progress to an objective. Progress only counts while the season is ACTIVE and is clamped to
     * {@code [0, required]}. On completion the objective, its points, and any crossed milestone's
     * pending reward record are written in one transaction; the reward is then delivered from the
     * durable outbox. No-op if already complete. Called from the admin command (server thread).
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

        long now = System.currentTimeMillis();
        ObjectiveTxn txn = db.callInTransaction(conn ->
                applyObjectiveOnConn(conn, uuid, seasonId, def.get(), objective.get(), amount, now));
        if (txn.status() == ObjectiveResult.Status.COMPLETED) {
            auditCompletion(uuid, seasonId, objectiveId, txn);
            if (txn.milestoneRecorded()) {
                deliverPendingMilestones(uuid);
            }
        }
        return new ObjectiveResult(txn.status(), txn.progress(), txn.completed());
    }

    /** One matching objective and the progress a game event contributes to it. */
    public record ObjectiveMatch(String objectiveId, int amount) {
    }

    /**
     * Applies progress to several objectives for one player <b>off the server thread</b>: all DB work
     * (objectives, points, and pending milestone records) runs in one transaction on the database
     * worker, then milestone reward delivery is marshalled back onto the server thread. Callers
     * (game-event listeners) compute matches cheaply and hand off here without blocking the tick.
     */
    public void advanceObjectivesAsync(UUID uuid, String seasonId, List<ObjectiveMatch> matches) {
        if (matches.isEmpty()) {
            return;
        }
        SeasonDefinition def = definition(seasonId).orElse(null);
        if (def == null) {
            return;
        }
        long now = System.currentTimeMillis();
        db.supplyAsync(conn -> {
            List<CompletedObjective> completed = new ArrayList<>();
            TransactionManager.execute(conn, c -> {
                completed.clear(); // guard against any partial fill if the body is re-entered
                for (ObjectiveMatch match : matches) {
                    ObjectiveDefinition objective = def.objective(match.objectiveId()).orElse(null);
                    if (objective == null) {
                        continue;
                    }
                    ObjectiveTxn txn = applyObjectiveOnConn(c, uuid, seasonId, def, objective, match.amount(), now);
                    if (txn.status() == ObjectiveResult.Status.COMPLETED) {
                        completed.add(new CompletedObjective(objective.id, txn));
                    }
                }
            });
            // The transaction committed: only now is it truthful to record completion audits and logs.
            boolean anyMilestone = false;
            for (CompletedObjective c : completed) {
                auditCompletion(uuid, seasonId, c.objectiveId(), c.txn());
                anyMilestone |= c.txn().milestoneRecorded();
            }
            return anyMilestone;
        }).thenAccept(anyMilestone -> {
            if (anyMilestone) {
                deliverPendingMilestonesOnServerThread(uuid);
            }
        }).exceptionally(t -> {
            LOGGER.warn("Async objective progress failed for {} in '{}'", uuid, seasonId, t);
            return null;
        });
    }

    /**
     * The shared transaction body: read, clamp, write objective, and on completion award points and
     * record any crossed milestone's pending reward — all atomically with the caller's transaction.
     */
    private ObjectiveTxn applyObjectiveOnConn(java.sql.Connection conn, UUID uuid, String seasonId,
                                              SeasonDefinition def, ObjectiveDefinition objective,
                                              int amount, long now) throws java.sql.SQLException {
        ObjectiveProgress current = repository.objective(conn, uuid, seasonId, objective.id).orElse(null);
        if (current != null && current.completed()) {
            return new ObjectiveTxn(ObjectiveResult.Status.ALREADY_COMPLETE, current.progress(), true, -1, -1, false);
        }
        int prev = current == null ? 0 : current.progress();
        int newProgress = Math.max(0, Math.min(objective.required, prev + amount));
        boolean completed = newProgress >= objective.required;
        repository.setObjective(conn, uuid, seasonId, objective.id, newProgress, completed);
        int oldPoints = -1;
        int newPoints = -1;
        boolean milestoneRecorded = false;
        if (completed) {
            oldPoints = repository.points(conn, uuid, seasonId);
            newPoints = Math.max(0, oldPoints + objective.points);
            repository.setPoints(conn, uuid, seasonId, newPoints);
            milestoneRecorded = recordCrossedMilestones(conn, uuid, seasonId, def, oldPoints, newPoints, now);
        }
        return new ObjectiveTxn(completed ? ObjectiveResult.Status.COMPLETED
                : ObjectiveResult.Status.PROGRESSED, newProgress, completed, oldPoints, newPoints, milestoneRecorded);
    }

    /**
     * Records the durable pending-reward record for each milestone crossed by a points change.
     * Returns true if at least one was recorded (so the caller can trigger delivery).
     */
    private boolean recordCrossedMilestones(java.sql.Connection conn, UUID uuid, String seasonId,
                                            @Nullable SeasonDefinition def, int oldPoints, int newPoints,
                                            long now) throws java.sql.SQLException {
        if (def == null || newPoints <= oldPoints) {
            return false;
        }
        boolean recorded = false;
        for (Milestone milestone : def.milestones) {
            if (milestone.reward != null && oldPoints < milestone.points && milestone.points <= newPoints) {
                repository.insertPendingMilestone(conn, uuid, seasonId, milestone.reward, now);
                recorded = true;
            }
        }
        return recorded;
    }

    /** Marshals milestone delivery onto the server thread; leaves it pending (for startup resume) if none. */
    private void deliverPendingMilestonesOnServerThread(UUID uuid) {
        MinecraftServer server = ServerHolder.get();
        if (server == null) {
            LOGGER.warn("[CV-SEASON-001] Milestone reward(s) pending for {} but no server available; "
                    + "they will be delivered on the next startup", uuid);
            return;
        }
        server.execute(() -> deliverPendingMilestones(uuid));
    }

    /**
     * Grants every pending milestone reward for a player from the durable outbox, deleting each once
     * its grant lands in an accepted state (delivered, queued, or already claimed). Must run where a
     * reward grant is safe: the server thread, or startup (no online players).
     */
    public void deliverPendingMilestones(UUID uuid) {
        List<SeasonRepository.PendingMilestone> pending =
                db.callSync(conn -> repository.pendingMilestones(conn, uuid));
        pending.forEach(this::deliverOne);
    }

    /** Re-delivers any milestone rewards left pending by a crash. Called on startup. Returns the count. */
    public int resumePendingMilestones() {
        List<SeasonRepository.PendingMilestone> pending = db.callSync(repository::allPendingMilestones);
        if (!pending.isEmpty()) {
            LOGGER.info("Resuming {} pending milestone reward(s)", pending.size());
        }
        pending.forEach(this::deliverOne);
        return pending.size();
    }

    /** Every milestone reward still owed across all players (the durable outbox). For admin inspection. */
    public List<SeasonRepository.PendingMilestone> listPendingMilestones() {
        return db.callSync(repository::allPendingMilestones);
    }

    /**
     * Explicitly drops a single pending milestone reward from the outbox without delivering it — for an
     * admin abandoning an entry that can never be delivered (e.g. a reward id removed from config). The
     * player does not receive it. Returns true if a row was removed.
     */
    public boolean abandonPendingMilestone(long id, String actor) {
        boolean removed = db.callSync(conn -> repository.deletePendingMilestone(conn, id)) > 0;
        if (removed) {
            audit.record(AuditEntry.builder(AuditType.ADMIN_COMMAND)
                    .source(actor).context("season reward abandon: pending #" + id));
            LOGGER.info("Abandoned pending milestone reward #{} ({})", id, actor);
        }
        return removed;
    }

    private void deliverOne(SeasonRepository.PendingMilestone pending) {
        RewardResult result = rewards.grant(pending.uuid(), pending.rewardId(), "season:" + pending.seasonId());
        if (result.status() == RewardStatus.SUCCESS || result.status() == RewardStatus.QUEUED
                || result.status() == RewardStatus.ALREADY_CLAIMED) {
            db.runSync(conn -> repository.deletePendingMilestone(conn, pending.id()));
        } else {
            LOGGER.warn("[CV-SEASON-002] Milestone reward '{}' for {} not delivered ({}); left pending",
                    pending.rewardId(), pending.uuid(), result.status());
        }
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
                                int oldPoints, int newPoints, boolean milestoneRecorded) {
    }

    /** A completion observed inside a transaction, held until the transaction commits so its audit is truthful. */
    private record CompletedObjective(String objectiveId, ObjectiveTxn txn) {
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
