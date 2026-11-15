package com.gramsetu.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Small in-memory fixed-window rate limiter keyed by an arbitrary string (the client IP).
 * Memory is bounded: expired windows are purged once the map grows, and past a hard cap new
 * keys are let through untracked (fail open) so a flood of distinct keys cannot exhaust memory.
 * State is per application instance; run behind a shared limiter if you scale out.
 */
public class FixedWindowRateLimiter {

    private static final int PURGE_THRESHOLD = 10_000;
    private static final int MAX_KEYS = 100_000;

    private static final class Window {
        volatile long start;
        int count;
        Window(long start) { this.start = start; }
    }

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, long windowMillis, LongSupplier clock) {
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    public FixedWindowRateLimiter(int limitPerMinute) {
        this(limitPerMinute, 60_000L, System::currentTimeMillis);
    }

    /** @return 0 when the request is allowed, otherwise the seconds until the window resets (&gt;= 1). */
    public long tryAcquire(String key) {
        long now = clock.getAsLong();
        if (windows.size() > PURGE_THRESHOLD) purge(now);
        if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) return 0;

        Window w = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (w) {
            if (now - w.start >= windowMillis) {
                w.start = now;
                w.count = 0;
            }
            if (w.count < limit) {
                w.count++;
                return 0;
            }
            return Math.max(1, (w.start + windowMillis - now + 999) / 1000);
        }
    }

    private void purge(long now) {
        windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMillis);
    }

    int trackedKeys() { return windows.size(); }
}
