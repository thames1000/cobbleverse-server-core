package com.thamescape.cobbleverse.core.persistence.repository;

import com.thamescape.cobbleverse.core.event.EventParticipant;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Event lifecycle state and participation (with score for leaderboards). */
public final class EventRepository {

    // --- State ------------------------------------------------------------------------------------

    public Optional<String> state(Connection conn, String eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT state FROM events WHERE event_id = ?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    /**
     * Upserts an event's state. {@code startedAt} is preserved once set (first activation);
     * {@code endedAt} is set when transitioning to a terminal state. Pass null to leave a timestamp
     * unchanged.
     */
    public void setState(Connection conn, String eventId, String state,
                         @Nullable Long startedAt, @Nullable Long endedAt, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO events(event_id, state, started_at, ended_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(event_id) DO UPDATE SET
                    state = excluded.state,
                    started_at = COALESCE(events.started_at, excluded.started_at),
                    ended_at = COALESCE(excluded.ended_at, events.ended_at),
                    updated_at = excluded.updated_at
                """)) {
            ps.setString(1, eventId);
            ps.setString(2, state);
            ps.setObject(3, startedAt);
            ps.setObject(4, endedAt);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    // --- Participation ----------------------------------------------------------------------------

    /** Adds a participant if not already joined. Returns true if this call added them. */
    public boolean join(Connection conn, String eventId, UUID uuid, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO event_participation(event_id, uuid, joined_at, score) "
                        + "VALUES (?, ?, ?, 0)")) {
            ps.setString(1, eventId);
            ps.setString(2, uuid.toString());
            ps.setLong(3, now);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean leave(Connection conn, String eventId, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM event_participation WHERE event_id = ? AND uuid = ?")) {
            ps.setString(1, eventId);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean isParticipant(Connection conn, String eventId, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM event_participation WHERE event_id = ? AND uuid = ?")) {
            ps.setString(1, eventId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** An atomic score change: the participant's score before and after. */
    public record ScoreChange(int oldScore, int newScore) {
    }

    /**
     * Atomically adds to a participant's score and returns the before/after values. Empty if the
     * player is not a participant. The read and update run on the same connection, so no other
     * database operation can interleave.
     */
    public Optional<ScoreChange> addScore(Connection conn, String eventId, UUID uuid, int amount)
            throws SQLException {
        int oldScore;
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT score FROM event_participation WHERE event_id = ? AND uuid = ?")) {
            select.setString(1, eventId);
            select.setString(2, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                oldScore = rs.getInt(1);
            }
        }
        int newScore = Math.max(0, oldScore + amount);
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE event_participation SET score = ? WHERE event_id = ? AND uuid = ?")) {
            update.setInt(1, newScore);
            update.setString(2, eventId);
            update.setString(3, uuid.toString());
            update.executeUpdate();
        }
        return Optional.of(new ScoreChange(oldScore, newScore));
    }

    /** Marks whether a completed event has finished distributing its rewards. */
    public void setRewardsDistributed(Connection conn, String eventId, boolean distributed)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE events SET rewards_distributed = ? WHERE event_id = ?")) {
            ps.setInt(1, distributed ? 1 : 0);
            ps.setString(2, eventId);
            ps.executeUpdate();
        }
    }

    /** Event ids that are COMPLETED but whose reward distribution did not finish (needs resuming). */
    public List<String> pendingRewardEventIds(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT event_id FROM events WHERE state = 'COMPLETED' AND rewards_distributed = 0");
             ResultSet rs = ps.executeQuery()) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        }
    }

    public int participantCount(Connection conn, String eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM event_participation WHERE event_id = ?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<EventParticipant> participants(Connection conn, String eventId) throws SQLException {
        return query(conn, eventId, 0);
    }

    public List<EventParticipant> leaderboard(Connection conn, String eventId, int limit) throws SQLException {
        return query(conn, eventId, limit);
    }

    private List<EventParticipant> query(Connection conn, String eventId, int limit) throws SQLException {
        String sql = "SELECT p.uuid, pr.last_known_name AS name, p.joined_at, p.score "
                + "FROM event_participation p LEFT JOIN player_profiles pr ON pr.uuid = p.uuid "
                + "WHERE p.event_id = ? ORDER BY p.score DESC, p.joined_at ASC"
                + (limit > 0 ? " LIMIT ?" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            if (limit > 0) {
                ps.setInt(2, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<EventParticipant> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new EventParticipant(UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"), rs.getLong("joined_at"), rs.getInt("score")));
                }
                return out;
            }
        }
    }
}
