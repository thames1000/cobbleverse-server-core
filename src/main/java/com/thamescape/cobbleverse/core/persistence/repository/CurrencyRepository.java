package com.thamescape.cobbleverse.core.persistence.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Internal currency balances and their transaction ledger. Balances are stored as exact strings. */
public final class CurrencyRepository {

    public BigDecimal balance(Connection conn, UUID uuid, String currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance FROM currency_balances WHERE uuid = ? AND currency = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new BigDecimal(rs.getString("balance")) : BigDecimal.ZERO;
            }
        }
    }

    public void setBalance(Connection conn, UUID uuid, String currency, BigDecimal balance)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO currency_balances(uuid, currency, balance)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, currency) DO UPDATE SET balance = excluded.balance
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            ps.setString(3, balance.toPlainString());
            ps.executeUpdate();
        }
    }

    public void recordTransaction(Connection conn, UUID uuid, String currency, BigDecimal amount,
                                  String type, long timestamp, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO currency_transactions(uuid, currency, amount, type, timestamp, reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency);
            ps.setString(3, amount.toPlainString());
            ps.setString(4, type);
            ps.setLong(5, timestamp);
            ps.setString(6, reason);
            ps.executeUpdate();
        }
    }
}
