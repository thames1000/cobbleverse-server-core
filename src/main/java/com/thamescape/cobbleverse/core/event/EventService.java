package com.thamescape.cobbleverse.core.event;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.repository.EventRepository;
import com.thamescape.cobbleverse.core.reward.RewardService;
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
        db.runSync(conn -> repository.setState(conn, eventId, target.name(), startedAt, endedAt, now));

        audit.record(AuditEntry.builder(AuditType.EVENT_STATE_CHANGED)
                .source(actor).context(eventId + ": " + current + " -> " + target));
        LOGGER.info("Event '{}' {} -> {}", eventId, current, target);

        if (target == EventState.ACTIVE) {
            audit.record(AuditEntry.builder(AuditType.EVENT_STARTED).source(actor).context(eventId));
        } else if (target == EventState.COMPLETED) {
            int granted = distributeRewards(def);
            audit.record(AuditEntry.builder(AuditType.EVENT_ENDED)
                    .source(actor).context(eventId + " (rewarded " + granted + " participant(s))"));
        } else if (target == EventState.CANCELLED) {
            audit.record(AuditEntry.builder(AuditType.EVENT_ENDED).source(actor).context(eventId + " (cancelled)"));
        }
        return Result.ok(eventId + " -> " + target);
    }

    private int distributeRewards(EventDefinition def) {
        if (def.rewards.isEmpty()) {
            return 0;
        }
        List<EventParticipant> participants = db.callSync(conn -> repository.participants(conn, def.id));
        for (EventParticipant participant : participants) {
            for (String rewardId : def.rewards) {
                rewards.grant(participant.uuid(), rewardId, "event:" + def.id);
            }
        }
        LOGGER.info("Event '{}' completed; distributed {} reward(s) to {} participant(s)",
                def.id, def.rewards.size(), participants.size());
        return participants.size();
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
        int rows = db.callSync(conn -> repository.addScore(conn, eventId, uuid, amount));
        return rows > 0 ? Result.ok("score updated") : Result.fail("player is not a participant");
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
