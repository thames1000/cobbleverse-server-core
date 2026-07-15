package com.thamescape.cobbleverse.core.persistence.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player statistics as a key/value store. */
public final class StatisticsRepository {

    /** Adds {@code delta} to a stat (creating it at 0 first). */
    public void increment(Connection conn, UUID uuid, String stat, long delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO player_statistics(uuid, stat, value)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, stat) DO UPDATE SET value = value + excluded.value
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, stat);
            ps.setLong(3, delta);
            ps.executeUpdate();
        }
    }

    public long value(Connection conn, UUID uuid, String stat) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM player_statistics WHERE uuid = ? AND stat = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, stat);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public Map<String, Long> all(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT stat, value FROM player_statistics WHERE uuid = ? ORDER BY stat")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> out = new LinkedHashMap<>();
                while (rs.next()) {
                    out.put(rs.getString("stat"), rs.getLong("value"));
                }
                return out;
            }
        }
    }
}
