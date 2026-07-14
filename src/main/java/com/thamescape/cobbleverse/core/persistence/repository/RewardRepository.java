package com.thamescape.cobbleverse.core.persistence.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reward claim dedup and the offline reward queue. */
public final class RewardRepository {

    /** A queued reward pending delivery. */
    public record QueuedReward(long id, String definitionId, String source) {
    }

    public boolean hasClaimed(Connection conn, UUID uuid, String definitionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM reward_claims WHERE uuid = ? AND definition_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, definitionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Records a claim, atomically. Returns true if this call inserted the row (first claim), false if
     * a claim already existed — the primary key makes this the authoritative dedup check.
     */
    public boolean insertClaimIfAbsent(Connection conn, UUID uuid, String definitionId,
                                       long claimedAt, String source) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO reward_claims(uuid, definition_id, claimed_at, source) "
                        + "VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, definitionId);
            ps.setLong(3, claimedAt);
            ps.setString(4, source);
            return ps.executeUpdate() == 1;
        }
    }

    public void queue(Connection conn, UUID uuid, String definitionId,
                      long queuedAt, String source) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO reward_queue(uuid, definition_id, queued_at, source) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, definitionId);
            ps.setLong(3, queuedAt);
            ps.setString(4, source);
            ps.executeUpdate();
        }
    }

    public List<QueuedReward> findQueued(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, definition_id, source FROM reward_queue WHERE uuid = ? ORDER BY id")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<QueuedReward> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new QueuedReward(rs.getLong("id"), rs.getString("definition_id"),
                            rs.getString("source")));
                }
                return out;
            }
        }
    }

    public void deleteQueued(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM reward_queue WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public long claimCount(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM reward_claims");
    }

    public long queueCount(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM reward_queue");
    }

    private long count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
