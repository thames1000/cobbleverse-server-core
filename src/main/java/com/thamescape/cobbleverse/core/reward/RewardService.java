package com.thamescape.cobbleverse.core.reward;

import com.thamescape.cobbleverse.core.audit.AuditEntry;
import com.thamescape.cobbleverse.core.audit.AuditType;
import com.thamescape.cobbleverse.core.audit.AuditService;
import com.thamescape.cobbleverse.core.config.ConfigManager;
import com.thamescape.cobbleverse.core.persistence.DatabaseManager;
import com.thamescape.cobbleverse.core.persistence.repository.RewardRepository;
import com.thamescape.cobbleverse.core.util.ServerHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The single path through which every reward is granted. Validates, executes each reward entry,
 * tracks per-entry results, records claims, queues rewards for offline players, dead-letters
 * repeatedly-failing deliveries, audits outcomes, and supports dry-run previews and retries.
 *
 * <p>Recovery model:
 * <ul>
 *   <li>A non-repeatable definition is "complete" only when every entry has succeeded — completion is
 *       tracked on the claim ({@code PARTIAL} → {@code COMPLETE}), not by mere presence of a claim.</li>
 *   <li>Re-running {@code grant} on a partial reward retries only the entries that have not yet
 *       succeeded (item/currency already granted are skipped), so retries never double-grant.</li>
 *   <li>Offline grants queue; a failing queued delivery retains its row, counts attempts, and
 *       dead-letters after {@code maxDeliveryAttempts} rather than being silently dropped.</li>
 * </ul>
 */
public final class RewardService {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/REWARD");

    private final ConfigManager config;
    private final DatabaseManager db;
    private final RewardRepository repository;
    private final RewardRegistry registry;
    private final AuditService audit;

    public RewardService(ConfigManager config, DatabaseManager db,
                         RewardRepository repository, RewardRegistry registry, AuditService audit) {
        this.config = config;
        this.db = db;
        this.repository = repository;
        this.registry = registry;
        this.audit = audit;
    }

    /** Outcome of an admin retry: how many dead rows were revived and how many delivered now. */
    public record RetryOutcome(int revived, int delivered, boolean online) {
    }

    public Optional<RewardDefinition> definition(String id) {
        return Optional.ofNullable(config.rewards().definitions.get(id));
    }

    public List<String> definitionIds() {
        return new ArrayList<>(config.rewards().definitions.keySet());
    }

    /** True only when a non-repeatable definition has been fully granted (all entries succeeded). */
    public boolean isFullyClaimed(UUID uuid, String definitionId) {
        return db.callSync(conn -> repository.isComplete(conn, uuid, definitionId));
    }

    public List<RewardRepository.QueuedReward> queueSnapshot(UUID uuid) {
        return db.callSync(conn -> repository.findAllQueued(conn, uuid));
    }

    /**
     * Grants a definition to a player. Offline players have the reward queued for delivery on join.
     * A fully-claimed non-repeatable reward returns {@code ALREADY_CLAIMED}; a partial one retries the
     * remaining entries.
     */
    public RewardResult grant(UUID uuid, String definitionId, String source) {
        RewardDefinition def = config.rewards().definitions.get(definitionId);
        if (def == null) {
            return new RewardResult(RewardStatus.UNKNOWN, "no reward definition '" + definitionId + "'");
        }
        if (!def.repeatable && isFullyClaimed(uuid, definitionId)) {
            return new RewardResult(RewardStatus.ALREADY_CLAIMED,
                    "already claimed '" + def.displayNameOrId() + "'");
        }

        MinecraftServer server = ServerHolder.get();
        ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(uuid);
        if (player == null) {
            long now = System.currentTimeMillis();
            db.runSync(conn -> repository.enqueueOrRevive(conn, uuid, definitionId, now, source));
            return new RewardResult(RewardStatus.QUEUED,
                    "player offline; queued '" + def.displayNameOrId() + "' for next join");
        }
        return deliver(uuid, def, source, player, server);
    }

    /** Executes queued rewards for a player who just joined; dead-letters ones that keep failing. */
    public int deliverQueued(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        MinecraftServer server = player.getServer();
        int maxAttempts = config.rewards().maxDeliveryAttempts;
        List<RewardRepository.QueuedReward> queued = db.callSync(conn -> repository.findDeliverable(conn, uuid));
        int delivered = 0;
        for (RewardRepository.QueuedReward q : queued) {
            RewardDefinition def = config.rewards().definitions.get(q.definitionId());
            if (def == null) {
                failQueue(uuid, q.id(), "unknown definition '" + q.definitionId() + "'", maxAttempts);
                continue;
            }
            RewardResult result = deliver(uuid, def, q.source(), player, server);
            switch (result.status()) {
                case SUCCESS, ALREADY_CLAIMED -> {
                    db.runSync(conn -> repository.deleteQueued(conn, q.id()));
                    if (result.status() == RewardStatus.SUCCESS) {
                        delivered++;
                    }
                }
                case PARTIAL -> {
                    failQueue(uuid, q.id(), summarise(result), maxAttempts);
                    delivered++; // partial progress was made this attempt
                }
                default -> failQueue(uuid, q.id(), result.status() + ": " + summarise(result), maxAttempts);
            }
        }
        return delivered;
    }

    /** Admin retry: revive dead-lettered rows (optionally one definition) and deliver now if online. */
    public RetryOutcome retry(UUID uuid, @Nullable String definitionId) {
        int revived = db.callSync(conn -> repository.reviveDead(conn, uuid, definitionId));
        MinecraftServer server = ServerHolder.get();
        ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(uuid);
        int delivered = player != null ? deliverQueued(player) : 0;
        return new RetryOutcome(revived, delivered, player != null);
    }

    /** Reserves the claim (if non-repeatable) and executes each not-yet-succeeded reward entry. */
    private RewardResult deliver(UUID uuid, RewardDefinition def, String source,
                                 ServerPlayerEntity player, MinecraftServer server) {
        long now = System.currentTimeMillis();
        boolean repeatable = def.repeatable;
        if (!repeatable) {
            db.runSync(conn -> repository.insertClaimIfAbsent(conn, uuid, def.id, now, source));
            if (db.callSync(conn -> repository.isComplete(conn, uuid, def.id))) {
                return new RewardResult(RewardStatus.ALREADY_CLAIMED,
                        "already claimed '" + def.displayNameOrId() + "'");
            }
        }

        Map<Integer, String> done = repeatable ? Map.of()
                : db.callSync(conn -> repository.entryResults(conn, uuid, def.id));
        RewardContext context = new RewardContext(uuid, player, server, source);
        List<String> lines = new ArrayList<>();
        int ok = 0;
        int total = def.rewards.size();

        for (int i = 0; i < total; i++) {
            RewardEntry entry = def.rewards.get(i);
            if (!repeatable && RewardRepository.ENTRY_SUCCESS.equals(done.get(i))) {
                ok++;
                lines.add("+ " + describe(entry) + " (already granted)");
                continue;
            }
            RewardResult result = executeEntry(entry, context);
            if (!repeatable) {
                int index = i;
                String entryStatus = result.ok() ? RewardRepository.ENTRY_SUCCESS : result.status().name();
                String error = result.ok() ? null : result.message();
                db.runSync(conn -> repository.upsertEntryResult(
                        conn, uuid, def.id, index, entryStatus, error, now));
            }
            if (result.ok()) {
                ok++;
            }
            lines.add(symbol(result.status()) + " " + result.message());
        }

        RewardStatus status = ok == total ? RewardStatus.SUCCESS
                : ok == 0 ? RewardStatus.FAILED : RewardStatus.PARTIAL;
        if (!repeatable && status == RewardStatus.SUCCESS) {
            db.runSync(conn -> repository.setClaimStatus(conn, uuid, def.id, RewardRepository.STATUS_COMPLETE));
        }

        AuditEntry.Builder auditEntry = AuditEntry.builder(AuditType.REWARD_GRANTED)
                .target(uuid).source(source).context(def.id + " -> " + status);
        if (status == RewardStatus.FAILED) {
            auditEntry.failure("no reward entries succeeded");
        }
        audit.record(auditEntry);
        if (status != RewardStatus.SUCCESS) {
            LOGGER.warn("Reward '{}' to {} ended {}: {}", def.id, uuid, status, lines);
        }
        return new RewardResult(status, def.displayNameOrId(), lines);
    }

    private void failQueue(UUID uuid, long id, String error, int maxAttempts) {
        db.runSync(conn -> repository.recordQueueFailure(conn, id, error, System.currentTimeMillis(), maxAttempts));
    }

    private RewardResult executeEntry(RewardEntry entry, RewardContext context) {
        Optional<RewardType> type = RewardType.fromId(entry.typeOrEmpty());
        if (type.isEmpty()) {
            return RewardResult.failed("unknown reward type '" + entry.typeOrEmpty() + "'");
        }
        Optional<RewardHandler> handler = registry.handler(type.get());
        if (handler.isEmpty()) {
            return RewardResult.unsupported("no handler for '" + type.get().id() + "'");
        }
        String problem = handler.get().validate(entry);
        if (problem != null) {
            return RewardResult.failed(problem);
        }
        try {
            return handler.get().execute(entry, context);
        } catch (Exception e) {
            LOGGER.warn("Reward entry '{}' threw: {}", type.get().id(), e.getMessage());
            return RewardResult.failed(type.get().id() + " failed: " + e.getMessage());
        }
    }

    /** Dry-run: describes what a definition would grant, without granting it. */
    public RewardResult preview(UUID uuid, String definitionId) {
        RewardDefinition def = config.rewards().definitions.get(definitionId);
        if (def == null) {
            return new RewardResult(RewardStatus.UNKNOWN, "no reward definition '" + definitionId + "'");
        }
        List<String> lines = new ArrayList<>();
        for (RewardEntry entry : def.rewards) {
            Optional<RewardType> type = RewardType.fromId(entry.typeOrEmpty());
            if (type.isEmpty()) {
                lines.add("? unknown type '" + entry.typeOrEmpty() + "'");
                continue;
            }
            Optional<RewardHandler> handler = registry.handler(type.get());
            lines.add(handler.map(h -> "- " + h.describe(entry))
                    .orElse("? unsupported type '" + type.get().id() + "'"));
        }
        boolean claimed = !def.repeatable && isFullyClaimed(uuid, definitionId);
        String header = def.displayNameOrId()
                + (def.repeatable ? " (repeatable)" : claimed ? " (already claimed)" : "");
        return new RewardResult(claimed ? RewardStatus.ALREADY_CLAIMED : RewardStatus.SUCCESS, header, lines);
    }

    private String describe(RewardEntry entry) {
        return RewardType.fromId(entry.typeOrEmpty())
                .flatMap(registry::handler)
                .map(h -> h.describe(entry))
                .orElse(entry.typeOrEmpty());
    }

    private static String summarise(RewardResult result) {
        return result.lines().stream().filter(l -> !l.startsWith("+")).findFirst().orElse(result.message());
    }

    private static String symbol(RewardStatus status) {
        return status == RewardStatus.SUCCESS ? "+" : status == RewardStatus.UNSUPPORTED ? "~" : "x";
    }
}
