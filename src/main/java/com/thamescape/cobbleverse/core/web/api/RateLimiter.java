package com.thamescape.cobbleverse.core.web.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fixed-window, per-client request rate limiter. Thread-safe; the client map is size-capped so a
 * flood of distinct source addresses cannot grow it without bound. A limit of {@code <= 0} disables
 * limiting entirely. Time is passed in so the logic is deterministically testable.
 */
public final class RateLimiter {

    private static final int MAX_CLIENTS = 4096;
    private static final long WINDOW_MS = 60_000L;

    private final int limitPerWindow;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limitPerMinute) {
        this.limitPerWindow = limitPerMinute;
    }

    /** True if the request from {@code clientId} is allowed at {@code nowMs}; false if over the limit. */
    public boolean tryAcquire(String clientId, long nowMs) {
        if (limitPerWindow <= 0) {
            return true;
        }
        if (windows.size() > MAX_CLIENTS) {
            windows.clear(); // coarse reset; normal client sets are small (esp. behind a proxy)
        }
        return windows.computeIfAbsent(clientId, k -> new Window(nowMs)).tryAcquire(nowMs, limitPerWindow);
    }

    private static final class Window {
        private long windowStart;
        private int count;

        Window(long nowMs) {
            this.windowStart = nowMs;
        }

        synchronized boolean tryAcquire(long nowMs, int limit) {
            if (nowMs - windowStart >= WINDOW_MS) {
                windowStart = nowMs;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
