package com.thamescape.cobbleverse.core.event;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.TransactionManager;
import com.thamescape.cobbleverse.core.persistence.repository.EventRepository;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import com.thamescape.cobbleverse.core.reward.RewardService;
import com.thamescape.cobbleverse.core.reward.RewardStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns event lifecycle and participation. Events are defined in {@code events.json}; their live state
 * and participants are stored in the database. Transitions are validated against the
 * {@link EventState} machine, and completing an event grants each participant the event's rewards
 * through the central {@link RewardService} (so they dedup and queue for offline players).
 *
 * <p>0.5.0 transitions are admin-driven (console-testable). Scheduled auto-transitions are a later
 * addition; {@code scheduledStart}/{@code scheduledEnd} are stored but not enforced yet.
 */
public final class EventService {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/EVENT");

    private final ConfigManager config;
    private final DatabaseManager db;
    private final EventRepository repository;
    private final RewardService rewards;
    private final AuditService audit;

    public EventService(ConfigManager config, DatabaseManager db, EventRepository repository,
                        RewardService rewards, AuditService audit) {
        this.config = config;
        this.db = db;
        this.repository = repository;
        this.rewards = rewards;
        this.audit = audit;
    }

    /** Result of an event action. */
    public record Result(boolean ok, String message) {
        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public Optional<EventDefinition> definition(String eventId) {
        return Optional.ofNullable(config.events().events.get(eventId));
    }

    public List<String> definitionIds() {
        return new ArrayList<>(config.events().events.keySet());
    }

    /** Current state, defaulting to {@link EventState#DRAFT} if the event has never transitioned. */
    public EventState state(String eventId) {
        return db.callSync(conn -> repository.state(conn, eventId))
                .map(EventState::valueOf).orElse(EventState.DRAFT);
    }

    // --- Lifecycle --------------------------------------------------------------------------------

    public Result transition(String eventId, EventState target, String actor) {
        EventDefinition def = config.events().events.get(eventId);
        if (def == null) {
            return Result.fail("no event definition '" + eventId + "'");
        }
        EventState current = state(eventId);
        if (current == target) {
            return Result.fail("event is already " + target);
        }
        if (!current.canTransitionTo(target)) {
            return Result.fail("cannot move " + current + " -> " + target);
        }

        long now = System.currentTimeMillis();
        Long startedAt = target == EventState.ACTIVE ? now : null;
        Long endedAt = target.terminal() ? now : null;

        if (target == EventState.COMPLETED) {
            // Mark completed AND reward-distribution pending in one transaction, so a crash before or
            // during distribution is always detected and resumed on the next startup.
            db.runSync(conn -> TransactionManager.execute(conn, c -> {
                repository.setState(c, eventId, target.name(), startedAt, endedAt, now);
                repository.setRewardsDistributed(c, eventId, false);
            }));
        } else {
            db.runSync(conn -> repository.setState(conn, eventId, target.name(), startedAt, endedAt, now));
        }

        audit.record(AuditEntry.builder(AuditType.EVENT_STATE_CHANGED)
                .source(actor).context(eventId + ": " + current + " -> " + target));
        LOGGER.info("Event '{}' {} -> {}", eventId, current, target);

        if (target == EventState.ACTIVE) {
            audit.record(AuditEntry.builder(AuditType.EVENT_STARTED).source(actor).context(eventId));
        } else if (target == EventState.COMPLETED) {
            DistributionResult dist = completeDistribution(def);
            AuditEntry.Builder ended = AuditEntry.builder(AuditType.EVENT_ENDED).source(actor)
                    .context(eventId + " (rewarded " + dist.participants() + " participant(s)"
                            + (dist.complete() ? ")" : ", " + dist.failures() + " grant(s) pending)"));
            if (!dist.complete()) {
                ended.failure(dist.failures() + " grant(s) not delivered; distribution pending");
            }
            audit.record(ended);
        } else if (target == EventState.CANCELLED) {
            audit.record(AuditEntry.builder(AuditType.EVENT_ENDED).source(actor).context(eventId + " (cancelled)"));
        }
        return Result.ok(eventId + " -> " + target);
    }

    /**
     * Re-runs reward distribution for any event that completed but did not finish rewarding (e.g. the
     * server crashed mid-distribution). Safe because {@code grant()} is idempotent. Missing-definition
     * events are left pending (never silently marked done). Called on startup.
     */
    public ResumeSummary resumePendingDistributions() {
        List<String> pending = db.callSync(repository::pendingRewardEventIds);
        int completed = 0;
        int stillPending = 0;
        int missingDefinition = 0;
        for (String eventId : pending) {
            EventDefinition def = config.events().events.get(eventId);
            if (def == null) {
                missingDefinition++;
                LOGGER.error("[CV-EVENT-002] Completed event '{}' has pending rewards but no definition "
                        + "in events.json; distribution stays PENDING until the definition is restored, "
                        + "or an admin runs '/cvcore event rewards abandon {}'", eventId, eventId);
                continue;
            }
            LOGGER.warn("Resuming interrupted reward distribution for event '{}'", eventId);
            if (completeDistribution(def).complete()) {
                completed++;
            } else {
                stillPending++;
            }
        }
        if (!pending.isEmpty()) {
            LOGGER.info("Event reward resume: {} found, {} completed, {} still pending, {} missing definition",
                    pending.size(), completed, stillPending, missingDefinition);
        }
        return new ResumeSummary(pending.size(), completed, stillPending, missingDefinition);
    }

    /** Explicitly abandons a pending event's reward distribution (admin action). */
    public Result abandonRewards(String eventId, String actor) {
        db.runSync(conn -> repository.setRewardsDistributed(conn, eventId, true));
        audit.record(AuditEntry.builder(AuditType.EVENT_STATE_CHANGED)
                .source(actor).context(eventId + ": reward distribution abandoned"));
        LOGGER.warn("Event '{}' reward distribution abandoned by {}", eventId, actor);
        return Result.ok("abandoned pending reward distribution for '" + eventId + "'");
    }

    /**
     * Distributes rewards to all participants. Marks the event's distribution complete <b>only if</b>
     * every grant landed in an accepted terminal state (delivered, queued for offline delivery, or
     * already claimed). If any grant failed, the event stays pending so the next startup retries it.
     */
    private DistributionResult completeDistribution(EventDefinition def) {
        List<EventParticipant> participants = db.callSync(conn -> repository.participants(conn, def.id));
        int failures = 0;
        for (EventParticipant participant : participants) {
            for (String rewardId : def.rewards) {
                RewardResult result = rewards.grant(participant.uuid(), rewardId, "event:" + def.id);
                if (!isAccepted(result.status())) {
                    failures++;
                    LOGGER.warn("Event '{}' reward '{}' to {} not delivered: {} ({})",
                            def.id, rewardId, participant.uuid(), result.status(), result.message());
                }
            }
        }
        boolean complete = failures == 0;
        if (complete) {
            db.runSync(conn -> repository.setRewardsDistributed(conn, def.id, true));
            LOGGER.info("Event '{}' distributed {} reward(s) to {} participant(s)",
                    def.id, def.rewards.size(), participants.size());
        } else {
            LOGGER.error("[CV-EVENT-001] Event '{}' distribution incomplete: {} grant(s) failed; "
                    + "distribution stays PENDING (retried on next startup, or abandon with "
                    + "'/cvcore event rewards abandon {}')", def.id, failures, def.id);
        }
        return new DistributionResult(participants.size(), complete, failures);
    }

    private static boolean isAccepted(RewardStatus status) {
        return status == RewardStatus.SUCCESS
                || status == RewardStatus.QUEUED
                || status == RewardStatus.ALREADY_CLAIMED;
    }

    /** Outcome of one distribution pass. */
    private record DistributionResult(int participants, boolean complete, int failures) {
    }

    /** Summary of a startup resume sweep. */
    public record ResumeSummary(int found, int completed, int stillPending, int missingDefinition) {
    }

    // --- Participation ----------------------------------------------------------------------------

    public Result join(UUID uuid, String eventId) {
        if (definition(eventId).isEmpty()) {
            return Result.fail("no event '" + eventId + "'");
        }
        EventState current = state(eventId);
        if (!current.joinable()) {
            return Result.fail("event '" + eventId + "' is not open to join (state " + current + ")");
        }
        boolean added = db.callSync(conn -> repository.join(conn, eventId, uuid, System.currentTimeMillis()));
        if (added) {
            audit.record(AuditEntry.builder(AuditType.EVENT_JOINED).target(uuid).source("event:" + eventId));
            return Result.ok("joined '" + eventId + "'");
        }
        return Result.ok("already joined '" + eventId + "'");
    }

    public Result leave(UUID uuid, String eventId) {
        boolean removed = db.callSync(conn -> repository.leave(conn, eventId, uuid));
        if (removed) {
            audit.record(AuditEntry.builder(AuditType.EVENT_LEFT).target(uuid).source("event:" + eventId));
            return Result.ok("left '" + eventId + "'");
        }
        return Result.fail("not a participant of '" + eventId + "'");
    }

    public Result addScore(UUID uuid, String eventId, int amount) {
        return db.callSync(conn -> repository.addScore(conn, eventId, uuid, amount))
                .map(change -> Result.ok("score " + change.oldScore() + " -> " + change.newScore()))
                .orElse(Result.fail("player is not a participant"));
    }

    public int participantCount(String eventId) {
        return db.callSync(conn -> repository.participantCount(conn, eventId));
    }

    public boolean isParticipant(UUID uuid, String eventId) {
        return db.callSync(conn -> repository.isParticipant(conn, eventId, uuid));
    }

    public List<EventParticipant> leaderboard(String eventId, int limit) {
        return db.callSync(conn -> repository.leaderboard(conn, eventId, limit));
    }
}
