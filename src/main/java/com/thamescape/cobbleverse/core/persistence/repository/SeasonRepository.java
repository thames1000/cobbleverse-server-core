package com.thamescape.cobbleverse.core.persistence.repository;

import com.thamescape.cobbleverse.core.season.ObjectiveProgress;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Season points, per-objective progress, and season lifecycle state. */
public final class SeasonRepository {

    /** A leaderboard row: player and their points. {@code name} may be null if never seen. */
    public record LeaderboardEntry(UUID uuid, @Nullable String name, int points) {
        public String label() {
            return name != null ? name : uuid.toString().substring(0, 8);
        }
    }

    /** Top players by points in a season (points > 0), highest first. */
    public List<LeaderboardEntry> topByPoints(Connection conn, String seasonId, int limit)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT p.uuid, pr.last_known_name AS name, p.points "
                        + "FROM season_progress p LEFT JOIN player_profiles pr ON pr.uuid = p.uuid "
                        + "WHERE p.season_id = ? AND p.points > 0 ORDER BY p.points DESC LIMIT ?")) {
            ps.setString(1, seasonId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<LeaderboardEntry> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new LeaderboardEntry(UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"), rs.getInt("points")));
                }
                return out;
            }
        }
    }

    // --- Points -----------------------------------------------------------------------------------

    public int points(Connection conn, UUID uuid, String seasonId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT points FROM season_progress WHERE uuid = ? AND season_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void setPoints(Connection conn, UUID uuid, String seasonId, int points) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO season_progress(uuid, season_id, points)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, season_id) DO UPDATE SET points = excluded.points
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            ps.setInt(3, points);
            ps.executeUpdate();
        }
    }

    // --- Objectives -------------------------------------------------------------------------------

    public Optional<ObjectiveProgress> objective(Connection conn, UUID uuid, String seasonId,
                                                 String objectiveId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT progress, completed FROM objective_progress "
                        + "WHERE uuid = ? AND season_id = ? AND objective_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            ps.setString(3, objectiveId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new ObjectiveProgress(objectiveId, rs.getInt("progress"),
                                rs.getInt("completed") != 0))
                        : Optional.empty();
            }
        }
    }

    public Map<String, ObjectiveProgress> objectives(Connection conn, UUID uuid, String seasonId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT objective_id, progress, completed FROM objective_progress "
                        + "WHERE uuid = ? AND season_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, ObjectiveProgress> out = new LinkedHashMap<>();
                while (rs.next()) {
                    String id = rs.getString("objective_id");
                    out.put(id, new ObjectiveProgress(id, rs.getInt("progress"),
                            rs.getInt("completed") != 0));
                }
                return out;
            }
        }
    }

    public void setObjective(Connection conn, UUID uuid, String seasonId, String objectiveId,
                             int progress, boolean completed) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO objective_progress(uuid, season_id, objective_id, progress, completed)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid, season_id, objective_id) DO UPDATE SET
                    progress = excluded.progress, completed = excluded.completed
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            ps.setString(3, objectiveId);
            ps.setInt(4, progress);
            ps.setInt(5, completed ? 1 : 0);
            ps.executeUpdate();
        }
    }

    // --- Pending milestone rewards (durable outbox) -----------------------------------------------

    /** An owed milestone reward grant recorded atomically with the objective/points that crossed it. */
    public record PendingMilestone(long id, UUID uuid, String seasonId, String rewardId) {
    }

    /** Records an owed milestone grant (idempotent per player/season/reward). */
    public void insertPendingMilestone(Connection conn, UUID uuid, String seasonId, String rewardId,
                                       long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO pending_milestone_rewards(uuid, season_id, reward_id, created_at) "
                        + "VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            ps.setString(3, rewardId);
            ps.setLong(4, now);
            ps.executeUpdate();
        }
    }

    public List<PendingMilestone> pendingMilestones(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, uuid, season_id, reward_id FROM pending_milestone_rewards "
                        + "WHERE uuid = ? ORDER BY id")) {
            ps.setString(1, uuid.toString());
            return readPending(ps);
        }
    }

    public List<PendingMilestone> allPendingMilestones(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, uuid, season_id, reward_id FROM pending_milestone_rewards ORDER BY id")) {
            return readPending(ps);
        }
    }

    /** Deletes a pending row by id; returns the number of rows removed (0 if the id was not present). */
    public int deletePendingMilestone(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM pending_milestone_rewards WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    private List<PendingMilestone> readPending(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<PendingMilestone> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new PendingMilestone(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                        rs.getString("season_id"), rs.getString("reward_id")));
            }
            return out;
        }
    }

    // --- Lifecycle --------------------------------------------------------------------------------

    public Optional<String> lifecycleState(Connection conn, String seasonId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state FROM season_lifecycle WHERE season_id = ?")) {
            ps.setString(1, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void setLifecycleState(Connection conn, String seasonId, String state, long now)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO season_lifecycle(season_id, state, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(season_id) DO UPDATE SET state = excluded.state, updated_at = excluded.updated_at
                """)) {
            ps.setString(1, seasonId);
            ps.setString(2, state);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }
}
