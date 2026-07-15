package com.thamescape.cobbleverse.core.persistence.repository;

import com.thamescape.cobbleverse.core.season.ObjectiveProgress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Season points, per-objective progress, and season lifecycle state. */
public final class SeasonRepository {

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
