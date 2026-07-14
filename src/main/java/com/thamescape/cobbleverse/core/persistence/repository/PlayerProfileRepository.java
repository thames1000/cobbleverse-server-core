package com.thamescape.cobbleverse.core.persistence.repository;

import com.thamescape.cobbleverse.core.player.PlayerProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Prepared-statement CRUD for {@code player_profiles}. All methods take the connection supplied by
 * {@link com.thamescape.cobbleverse.core.persistence.DatabaseManager} and run on its worker thread.
 */
public final class PlayerProfileRepository {

    public Optional<PlayerProfile> find(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, last_known_name, first_joined_at, last_joined_at, playtime_seconds "
                        + "FROM player_profiles WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<PlayerProfile> findByName(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, last_known_name, first_joined_at, last_joined_at, playtime_seconds "
                        + "FROM player_profiles WHERE last_known_name = ? COLLATE NOCASE "
                        + "ORDER BY last_joined_at DESC LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Inserts a profile only if none exists for its UUID, atomically. Returns true if this call
     * inserted the row. Use to create a profile without racing a concurrent create/join.
     */
    public boolean insertIfAbsent(Connection conn, PlayerProfile profile) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO player_profiles"
                        + "(uuid, last_known_name, first_joined_at, last_joined_at, playtime_seconds) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, profile.uuid().toString());
            ps.setString(2, profile.lastKnownName());
            ps.setLong(3, profile.firstJoinedAt());
            ps.setLong(4, profile.lastJoinedAt());
            ps.setLong(5, profile.playtimeSeconds());
            return ps.executeUpdate() == 1;
        }
    }

    /** Inserts or updates a profile by UUID. {@code first_joined_at} is preserved on update. */
    public void upsert(Connection conn, PlayerProfile profile) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO player_profiles
                    (uuid, last_known_name, first_joined_at, last_joined_at, playtime_seconds)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_known_name  = excluded.last_known_name,
                    last_joined_at   = excluded.last_joined_at,
                    playtime_seconds = excluded.playtime_seconds
                """)) {
            ps.setString(1, profile.uuid().toString());
            ps.setString(2, profile.lastKnownName());
            ps.setLong(3, profile.firstJoinedAt());
            ps.setLong(4, profile.lastJoinedAt());
            ps.setLong(5, profile.playtimeSeconds());
            ps.executeUpdate();
        }
    }

    public long count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM player_profiles");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private PlayerProfile map(ResultSet rs) throws SQLException {
        return new PlayerProfile(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("last_known_name"),
                rs.getLong("first_joined_at"),
                rs.getLong("last_joined_at"),
                rs.getLong("playtime_seconds"));
    }
}
