package com.gramsetu.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 60_000, now::get);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        assertEquals(0, limiter.tryAcquire("a"));
        assertEquals(0, limiter.tryAcquire("a"));
        assertEquals(0, limiter.tryAcquire("a"));
        long retry = limiter.tryAcquire("a");
        assertTrue(retry >= 1 && retry <= 60, "retry-after in seconds, was " + retry);
    }

    @Test
    void retryAfterShrinksAsTheWindowAdvances() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("a");
        long first = limiter.tryAcquire("a");
        now.addAndGet(30_000);
        long later = limiter.tryAcquire("a");
        assertTrue(later < first, first + " -> " + later);
    }

    @Test
    void windowResetsAfterItExpires() {
        for (int i = 0; i < 4; i++) limiter.tryAcquire("a");
        now.addAndGet(60_000);
        assertEquals(0, limiter.tryAcquire("a"));
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 4; i++) limiter.tryAcquire("a");
        assertEquals(0, limiter.tryAcquire("b"));
    }

    @Test
    void expiredEntriesArePurgedSoMemoryStaysBounded() {
        for (int i = 0; i < 10_050; i++) limiter.tryAcquire("ip-" + i);
        now.addAndGet(61_000);
        limiter.tryAcquire("fresh");
        assertTrue(limiter.trackedKeys() < 100, "tracked keys: " + limiter.trackedKeys());
    }

    @Test
    void rejectsInvalidLimit() {
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowRateLimiter(0, 1000, now::get));
    }
}
