package com.thamescape.cobbleverse.core.persistence;

import com.thamescape.cobbleverse.core.util.error.DatabaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite-backed {@link DatabaseProvider}. The driver is loaded explicitly by class name — reliable
 * under Fabric's classloader where JDBC's {@code ServiceLoader} auto-discovery across nested jars is
 * not guaranteed.
 */
public final class SqliteDatabaseProvider implements DatabaseProvider {

    private static final String DRIVER_CLASS = "org.sqlite.JDBC";

    private final Path dbFile;

    public SqliteDatabaseProvider(Path dbFile) {
        this.dbFile = dbFile;
    }

    @Override
    public String type() {
        return "sqlite";
    }

    @Override
    public Connection connect() throws SQLException {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("CV-DB-001",
                    "SQLite driver (" + DRIVER_CLASS + ") not on the classpath", e);
        }

        try {
            Path parent = dbFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new DatabaseException("CV-DB-002",
                    "Could not create database directory for " + dbFile, e);
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        applyPragmas(connection);
        return connection;
    }

    private void applyPragmas(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA busy_timeout = 5000");
        }
    }

    @Override
    public String describe() {
        return "SQLite (" + dbFile + ")";
    }
}
