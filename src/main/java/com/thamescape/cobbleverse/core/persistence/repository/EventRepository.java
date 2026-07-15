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

    /** Adds to a participant's score. Returns rows updated (0 if they aren't a participant). */
    public int addScore(Connection conn, String eventId, UUID uuid, int amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE event_participation SET score = MAX(0, score + ?) WHERE event_id = ? AND uuid = ?")) {
            ps.setInt(1, amount);
            ps.setString(2, eventId);
            ps.setString(3, uuid.toString());
            return ps.executeUpdate();
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
