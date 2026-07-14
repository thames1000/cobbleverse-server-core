package com.thamescape.cobbleverse.core.persistence.repository;

import com.thamescape.cobbleverse.core.audit.AuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Append-only writes to {@code audit_log}. */
public final class AuditRepository {

    public void insert(Connection conn, AuditEntry entry) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO audit_log
                    (timestamp, action, actor, actor_name, target, source,
                     context, before_value, after_value, success, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, entry.timestamp().toEpochMilli());
            ps.setString(2, entry.action().name());
            ps.setString(3, entry.actor() != null ? entry.actor().toString() : null);
            ps.setString(4, entry.actorName());
            ps.setString(5, entry.target() != null ? entry.target().toString() : null);
            ps.setString(6, entry.source());
            ps.setString(7, entry.context());
            ps.setString(8, entry.before());
            ps.setString(9, entry.after());
            ps.setInt(10, entry.success() ? 1 : 0);
            ps.setString(11, entry.failureReason());
            ps.executeUpdate();
        }
    }

    public long count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM audit_log");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
