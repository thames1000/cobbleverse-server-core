package com.thamescape.cobbleverse.core.web.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic tests for the fixed-window {@link RateLimiter} (time is injected). */
class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenRejects() {
        RateLimiter limiter = new RateLimiter(3);
        long now = 1_000L;
        assertTrue(limiter.tryAcquire("a", now));
        assertTrue(limiter.tryAcquire("a", now));
        assertTrue(limiter.tryAcquire("a", now));
        assertFalse(limiter.tryAcquire("a", now), "the 4th request in the window is over the limit");
    }

    @Test
    void theWindowResetsAfterAMinute() {
        RateLimiter limiter = new RateLimiter(1);
        assertTrue(limiter.tryAcquire("a", 0));
        assertFalse(limiter.tryAcquire("a", 100));
        assertTrue(limiter.tryAcquire("a", 60_000), "a new window allows requests again");
    }

    @Test
    void clientsAreLimitedIndependently() {
        RateLimiter limiter = new RateLimiter(1);
        assertTrue(limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("b", 0), "a different client has its own budget");
    }

    @Test
    void zeroOrLessDisablesLimiting() {
        RateLimiter limiter = new RateLimiter(0);
        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.tryAcquire("a", 0), "limit <= 0 disables limiting");
        }
    }
}
