package com.thamescape.cobbleverse.core.persistence;

import com.thamescape.cobbleverse.core.persistence.migration.Migration;
import com.thamescape.cobbleverse.core.persistence.migration.V001InitialSchema;
import com.thamescape.cobbleverse.core.persistence.migration.V002RewardsAndCurrency;
import com.thamescape.cobbleverse.core.persistence.migration.V003RewardRecovery;
import com.thamescape.cobbleverse.core.persistence.migration.V004SeasonSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies pending schema migrations in ascending version order, each in its own transaction, and
 * records applied versions in {@code schema_version}. Idempotent: already-applied migrations are
 * skipped, so it is safe to run on every startup.
 */
public final class MigrationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/DATABASE");

    private final List<Migration> migrations;

    public MigrationManager(List<Migration> migrations) {
        this.migrations = new ArrayList<>(migrations);
        this.migrations.sort(Comparator.comparingInt(Migration::version));
    }

    /** The built-in migration set for this build. */
    public static MigrationManager withDefaults() {
        return new MigrationManager(List.of(
                new V001InitialSchema(),
                new V002RewardsAndCurrency(),
                new V003RewardRecovery(),
                new V004SeasonSchema()));
    }

    /** Highest migration version this build ships. */
    public int latestVersion() {
        return migrations.isEmpty() ? 0 : migrations.get(migrations.size() - 1).version();
    }

    /** Current schema version recorded in the database (0 if fresh). */
    public int currentVersion(DatabaseManager db) {
        return db.callSync(this::readCurrentVersion);
    }

    /** Applies any pending migrations. Runs on the database worker thread. */
    public void migrate(DatabaseManager db) {
        db.runSync(conn -> {
            ensureVersionTable(conn);
            int current = readCurrentVersion(conn);
            int applied = 0;
            for (Migration migration : migrations) {
                if (migration.version() <= current) {
                    continue;
                }
                TransactionManager.execute(conn, c -> {
                    migration.apply(c);
                    record(c, migration);
                });
                LOGGER.info("Applied migration V{} ({})", migration.version(), migration.name());
                applied++;
            }
            if (applied == 0) {
                LOGGER.info("Schema up to date (version {})", current);
            } else {
                LOGGER.info("Schema migrated to version {}", latestVersion());
            }
        });
    }

    private void ensureVersionTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version    INTEGER PRIMARY KEY,
                        name       TEXT    NOT NULL,
                        applied_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    private int readCurrentVersion(Connection conn) throws SQLException {
        ensureVersionTable(conn);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void record(Connection conn, Migration migration) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO schema_version(version, name, applied_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, migration.version());
            ps.setString(2, migration.name());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
