package com.thamescape.cobbleverse.core.web.api;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the embedded JDK {@link HttpServer} that serves the read-only API. A bind failure (port in use,
 * bad address) is logged and leaves the API simply <b>off</b> — it never aborts the Minecraft server.
 * Requests are handled on a small daemon thread pool; data access marshals to the DB worker as usual.
 */
public final class ApiServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleverseCore/WEB");
    private static final int THREADS = 4;

    private final String bindAddress;
    private final int port;
    private final ApiRouter router;

    private HttpServer server;
    private ExecutorService executor;

    public ApiServer(String bindAddress, int port, ApiRouter router) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.router = router;
    }

    /** Binds and starts the server. No-op if already running or if the bind fails (logged). */
    public synchronized void start() {
        if (server != null) {
            return;
        }
        HttpServer created;
        try {
            created = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[CV-WEB-010] Could not bind web API to {}:{}; API disabled: {}",
                    bindAddress, port, e.toString());
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        executor = Executors.newFixedThreadPool(THREADS, runnable -> {
            Thread thread = new Thread(runnable, "cvcore-api-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        created.setExecutor(executor);
        created.createContext("/", router);
        created.start();
        this.server = created;
        LOGGER.info("Web API listening on http://{}:{}", bindAddress, boundPort());
    }

    /** Stops the server and its thread pool. Safe to call when not running. */
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    /** The actual bound port (useful when the configured port is 0 = ephemeral, e.g. in tests). */
    public synchronized int boundPort() {
        return server == null ? -1 : server.getAddress().getPort();
    }
}
