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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single path through which every reward is granted. Validates, executes each reward entry,
 * records a claim (enforcing "claim once" for non-repeatable definitions), queues rewards for offline
 * players, audits the outcome, and supports dry-run previews.
 *
 * <p>Non-repeatable definitions reserve their claim <i>before</i> execution, so a reward can never be
 * granted twice even under concurrency or partial failure.
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

    public Optional<RewardDefinition> definition(String id) {
        return Optional.ofNullable(config.rewards().definitions.get(id));
    }

    public List<String> definitionIds() {
        return new ArrayList<>(config.rewards().definitions.keySet());
    }

    public boolean hasClaimed(UUID uuid, String definitionId) {
        return db.callSync(conn -> repository.hasClaimed(conn, uuid, definitionId));
    }

    /**
     * Grants a definition to a player. If they are offline the reward is queued for delivery on their
     * next join. Returns a result describing what happened.
     */
    public RewardResult grant(UUID uuid, String definitionId, String source) {
        RewardDefinition def = config.rewards().definitions.get(definitionId);
        if (def == null) {
            return new RewardResult(RewardStatus.UNKNOWN, "no reward definition '" + definitionId + "'");
        }
        if (!def.repeatable && hasClaimed(uuid, definitionId)) {
            return new RewardResult(RewardStatus.ALREADY_CLAIMED,
                    "already claimed '" + def.displayNameOrId() + "'");
        }

        MinecraftServer server = ServerHolder.get();
        ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(uuid);
        if (player == null) {
            db.runSync(conn -> repository.queue(conn, uuid, definitionId, System.currentTimeMillis(), source));
            return new RewardResult(RewardStatus.QUEUED,
                    "player offline; queued '" + def.displayNameOrId() + "' for next join");
        }
        return deliver(uuid, def, source, player, server);
    }

    /** Executes any queued rewards for a player who just joined, then clears them from the queue. */
    public int deliverQueued(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        MinecraftServer server = player.getServer();
        List<RewardRepository.QueuedReward> queued =
                db.callSync(conn -> repository.findQueued(conn, uuid));
        int delivered = 0;
        for (RewardRepository.QueuedReward q : queued) {
            RewardDefinition def = config.rewards().definitions.get(q.definitionId());
            if (def != null) {
                RewardResult result = deliver(uuid, def, q.source(), player, server);
                if (result.status() == RewardStatus.SUCCESS || result.status() == RewardStatus.PARTIAL) {
                    delivered++;
                }
            }
            db.runSync(conn -> repository.deleteQueued(conn, q.id()));
        }
        return delivered;
    }

    /** Reserves the claim (if non-repeatable) and executes every reward entry. */
    private RewardResult deliver(UUID uuid, RewardDefinition def, String source,
                                 ServerPlayerEntity player, MinecraftServer server) {
        if (!def.repeatable) {
            boolean reserved = db.callSync(conn -> repository.insertClaimIfAbsent(
                    conn, uuid, def.id, System.currentTimeMillis(), source));
            if (!reserved) {
                return new RewardResult(RewardStatus.ALREADY_CLAIMED,
                        "already claimed '" + def.displayNameOrId() + "'");
            }
        }

        RewardContext context = new RewardContext(uuid, player, server, source);
        List<String> lines = new ArrayList<>();
        int ok = 0;
        int total = def.rewards.size();
        for (RewardEntry entry : def.rewards) {
            RewardResult entryResult = executeEntry(entry, context);
            lines.add(symbol(entryResult.status()) + " " + entryResult.message());
            if (entryResult.ok()) {
                ok++;
            }
        }

        RewardStatus status = ok == total ? RewardStatus.SUCCESS
                : ok == 0 ? RewardStatus.FAILED : RewardStatus.PARTIAL;
        AuditEntry.Builder entry = AuditEntry.builder(AuditType.REWARD_GRANTED)
                .target(uuid)
                .source(source)
                .context(def.id + " -> " + status);
        if (status == RewardStatus.FAILED) {
            entry.failure("no reward entries succeeded");
        }
        audit.record(entry);
        if (status != RewardStatus.SUCCESS) {
            LOGGER.warn("Reward '{}' to {} ended {}: {}", def.id, uuid, status, lines);
        }
        return new RewardResult(status, def.displayNameOrId(), lines);
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
        boolean claimed = !def.repeatable && hasClaimed(uuid, definitionId);
        String header = def.displayNameOrId()
                + (def.repeatable ? " (repeatable)" : claimed ? " (already claimed)" : "");
        return new RewardResult(claimed ? RewardStatus.ALREADY_CLAIMED : RewardStatus.SUCCESS, header, lines);
    }

    private static String symbol(RewardStatus status) {
        return status == RewardStatus.SUCCESS ? "+" : status == RewardStatus.UNSUPPORTED ? "~" : "x";
    }
}
