package com.thamescape.cobbleverse.core.persistence.repository;

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reward claim state, per-entry results, and the offline delivery queue.
 *
 * <p>A non-repeatable claim has a {@code status}: {@code PARTIAL} while some entries remain, promoted
 * to {@code COMPLETE} once all succeed. Per-entry results let a retry skip entries already granted.
 * Queue rows track delivery attempts and dead-letter after a configured maximum.
 */
public final class RewardRepository {

    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String ENTRY_SUCCESS = "SUCCESS";
    public static final String QUEUE_PENDING = "pending";
    public static final String QUEUE_DEAD = "dead";

    /** A queued reward row. */
    public record QueuedReward(long id, String definitionId, String source, int attemptCount, String status) {
    }

    // --- Claims -----------------------------------------------------------------------------------

    /** Inserts a claim as {@code PARTIAL} if absent. Returns true if this call inserted it. */
    public boolean insertClaimIfAbsent(Connection conn, UUID uuid, String definitionId,
                                       long claimedAt, String source) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO reward_claims(uuid, definition_id, claimed_at, source, status) "
                        + "VALUES (?, ?, ?, ?, '" + STATUS_PARTIAL + "')")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, definitionId);
            ps.setLong(3, claimedAt);
            ps.setString(4, source);
            return ps.executeUpdate() == 1;
        }
    }

    public Optional<String> claimStatus(Connection conn, UUID uuid, String definitionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM reward_claims WHERE uuid = ? AND definition_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, definitionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public boolean isComplete(Connection conn, UUID uuid, String definitionId) throws SQLException {
        return claimStatus(conn, uuid, definitionId).map(STATUS_COMPLETE::equals).orElse(false);
    }

    public void setClaimStatus(Connection conn, UUID uuid, String definitionId, String status)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE reward_claims SET status = ? WHERE uuid = ? AND definition_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, uuid.toString());
            ps.setString(3, definitionId);
            ps.executeUpdate();
        }
    }

    // --- Per-entry results ------------------------------------------------------------------------

    /** Map of entry index to its recorded status for a (player, definition). */
    public Map<Integer, String> entryResults(Connection conn, UUID uuid, String definitionId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT entry_index, status FROM reward_entry_results "
                        + "WHERE uuid = ? AND definition_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, definitionId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, String> out = new HashMap<>();
                while (rs.next()) {
                    out.put(rs.getInt("entry_index"), rs.getString("status"));
                }
                return out;
            }
        }
    }

    public void upsertEntryResult(Connection conn, UUID uuid, String definitionId, int entryIndex,
                                  String status, @Nullable String error, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO reward_entry_results(uuid, definition_id, entry_index, status, last_error, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, definition_id, entry_index) DO UPDATE SET
                    status = excluded.status, last_error = excluded.last_error, updated_at = excluded.updated_at
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, definitionId);
            ps.setInt(3, entryIndex);
            ps.setString(4, status);
            ps.setString(5, error);
            ps.setLong(6, now);
            ps.executeUpdate();
        }
    }

    // --- Queue ------------------------------------------------------------------------------------

    /** Ensures a single pending queue row per (player, definition), reviving/resetting any existing row. */
    public void enqueueOrRevive(Connection conn, UUID uuid, String definitionId,
                                long now, String source) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE reward_queue SET status = '" + QUEUE_PENDING + "', attempt_count = 0, "
                        + "last_error = NULL, queued_at = ?, source = ? "
                        + "WHERE uuid = ? AND definition_id = ?")) {
            update.setLong(1, now);
            update.setString(2, source);
            update.setString(3, uuid.toString());
            update.setString(4, definitionId);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO reward_queue(uuid, definition_id, queued_at, source, status) "
                        + "VALUES (?, ?, ?, ?, '" + QUEUE_PENDING + "')")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, definitionId);
            insert.setLong(3, now);
            insert.setString(4, source);
            insert.executeUpdate();
        }
    }

    /** Queue rows eligible for delivery (not dead-lettered). */
    public List<QueuedReward> findDeliverable(Connection conn, UUID uuid) throws SQLException {
        return queryQueue(conn,
                "SELECT id, definition_id, source, attempt_count, status FROM reward_queue "
                        + "WHERE uuid = ? AND status != '" + QUEUE_DEAD + "' ORDER BY id", uuid);
    }

    /** All queue rows for a player, including dead-lettered (for inspection). */
    public List<QueuedReward> findAllQueued(Connection conn, UUID uuid) throws SQLException {
        return queryQueue(conn,
                "SELECT id, definition_id, source, attempt_count, status FROM reward_queue "
                        + "WHERE uuid = ? ORDER BY id", uuid);
    }

    /** Records a failed delivery attempt, dead-lettering the row once it reaches {@code maxAttempts}. */
    public void recordQueueFailure(Connection conn, long id, @Nullable String error,
                                   long now, int maxAttempts) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE reward_queue SET attempt_count = attempt_count + 1, last_attempt_at = ?, "
                        + "last_error = ?, status = CASE WHEN attempt_count + 1 >= ? THEN '" + QUEUE_DEAD
                        + "' ELSE '" + QUEUE_PENDING + "' END WHERE id = ?")) {
            ps.setLong(1, now);
            ps.setString(2, error);
            ps.setInt(3, maxAttempts);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    /** Revives dead-lettered rows for a player (optionally one definition) back to pending. Returns count. */
    public int reviveDead(Connection conn, UUID uuid, @Nullable String definitionId) throws SQLException {
        String sql = "UPDATE reward_queue SET status = '" + QUEUE_PENDING + "', attempt_count = 0, "
                + "last_error = NULL WHERE uuid = ? AND status = '" + QUEUE_DEAD + "'"
                + (definitionId != null ? " AND definition_id = ?" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (definitionId != null) {
                ps.setString(2, definitionId);
            }
            return ps.executeUpdate();
        }
    }

    public void deleteQueued(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM reward_queue WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public long queueCount(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM reward_queue");
    }

    public long deadCount(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM reward_queue WHERE status = '" + QUEUE_DEAD + "'");
    }

    public long claimCount(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM reward_claims");
    }

    private List<QueuedReward> queryQueue(Connection conn, String sql, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<QueuedReward> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new QueuedReward(rs.getLong("id"), rs.getString("definition_id"),
                            rs.getString("source"), rs.getInt("attempt_count"), rs.getString("status")));
                }
                return out;
            }
        }
    }

    private long count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
