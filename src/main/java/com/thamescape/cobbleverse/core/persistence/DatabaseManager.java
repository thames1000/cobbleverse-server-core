package com.thamescape.cobbleverse.core.persistence;

import com.thamescape.cobbleverse.core.util.error.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the single database connection and a dedicated single worker thread. All SQL runs on that
 * thread, so the connection is never touched concurrently — the simplest correct model for SQLite,
 * and it keeps database work off the server thread as required.
 *
 * <p>Startup work (opening the connection, running migrations) uses the synchronous {@code *Sync}
 * methods, which still execute on the worker thread and block the caller for the result. Runtime
 * work uses the asynchronous methods and never blocks the server thread.
 */
public final class DatabaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/DATABASE");
    private static final long SLOW_WARN_MS = 50;
    private static final long SLOW_ERROR_MS = 250;

    private final DatabaseProvider provider;
    private final ExecutorService executor;
    private final AtomicInteger inFlight = new AtomicInteger();

    private volatile Connection connection;
    private volatile boolean connected;

    public DatabaseManager(DatabaseProvider provider) {
        this.provider = provider;
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "CobbleverseCore-DB");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    /** Opens the connection on the worker thread. Blocks until ready; throws on failure. */
    public void init() {
        try {
            executor.submit(() -> {
                this.connection = provider.connect();
                this.connected = true;
                return null;
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("CV-DB-003", "Interrupted while opening database", e);
        } catch (ExecutionException e) {
            throw new DatabaseException("CV-DB-003", "Failed to open database: "
                    + e.getCause().getMessage(), e.getCause());
        }
        LOGGER.info("Connected: {}", provider.describe());
    }

    public boolean isConnected() {
        return connected;
    }

    public String describe() {
        return provider.describe();
    }

    /** Number of database tasks currently queued or running. */
    public int pending() {
        return inFlight.get();
    }

    // --- Synchronous (startup only) ---------------------------------------------------------------

    /** Runs work on the worker thread and blocks for the result. Use during startup/migrations. */
    public <T> T callSync(SqlFunction<T> work) {
        try {
            return submit(work).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("CV-DB-004", "Interrupted during database work", e);
        } catch (ExecutionException e) {
            throw wrap(e.getCause());
        }
    }

    public void runSync(SqlConsumer work) {
        callSync(conn -> {
            work.accept(conn);
            return null;
        });
    }

    /** Runs {@code work} inside a transaction on the worker thread and returns its result. */
    public <T> T callInTransaction(SqlFunction<T> work) {
        return callSync(conn -> {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        });
    }

    // --- Asynchronous (runtime) -------------------------------------------------------------------

    /** Runs work on the worker thread without blocking. Failures complete the future exceptionally. */
    public <T> CompletableFuture<T> supplyAsync(SqlFunction<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        inFlight.incrementAndGet();
        executor.execute(() -> {
            long startNanos = System.nanoTime();
            try {
                future.complete(work.apply(requireConnection()));
            } catch (Throwable t) {
                future.completeExceptionally(wrap(t));
            } finally {
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                if (elapsedMs >= SLOW_ERROR_MS) {
                    LOGGER.error("Slow database operation: {} ms (threshold {} ms)", elapsedMs, SLOW_ERROR_MS);
                } else if (elapsedMs >= SLOW_WARN_MS) {
                    LOGGER.warn("Slow database operation: {} ms (threshold {} ms)", elapsedMs, SLOW_WARN_MS);
                }
                inFlight.decrementAndGet();
            }
        });
        return future;
    }

    public CompletableFuture<Void> runAsync(SqlConsumer work) {
        return supplyAsync(conn -> {
            work.accept(conn);
            return null;
        });
    }

    /** Runs work inside a transaction on the worker thread, committing or rolling back. */
    public CompletableFuture<Void> runInTransactionAsync(SqlConsumer work) {
        return runAsync(conn -> TransactionManager.execute(conn, work));
    }

    /** Flushes the write-ahead log into the main database file (used before taking a file backup). */
    public void checkpoint() {
        runSync(conn -> {
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        });
    }

    // --- Lifecycle --------------------------------------------------------------------------------

    /** Awaits queued work, then closes the connection. Safe to call once on shutdown. */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                LOGGER.warn("Database tasks did not finish within 15s; forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        connected = false;
        Connection conn = this.connection;
        if (conn != null) {
            try {
                conn.close();
                LOGGER.info("Database connection closed");
            } catch (SQLException e) {
                LOGGER.warn("Error closing database connection: {}", e.getMessage());
            }
        }
    }

    private <T> CompletableFuture<T> submit(SqlFunction<T> work) {
        return supplyAsync(work);
    }

    private Connection requireConnection() {
        Connection conn = this.connection;
        if (conn == null) {
            throw new DatabaseException("CV-DB-005", "Database used before init()");
        }
        return conn;
    }

    private static DatabaseException wrap(Throwable t) {
        if (t instanceof DatabaseException de) {
            return de;
        }
        return new DatabaseException("CV-DB-006", "Database operation failed: " + t.getMessage(), t);
    }
}
