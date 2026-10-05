package dev.vinyl.poc.discogs;

import java.util.function.LongSupplier;

/**
 * Keeps us under Discogs' 60 requests per minute: at least one second between requests, and a full
 * minute of waiting when the response says no requests remain (or on HTTP 429).
 */
public class Throttle {

    public interface Sleeper {
        void sleep(long millis);
    }

    private static final long MIN_INTERVAL_MS = 1000;
    private static final long BLOCK_MS = 60_000;

    private final LongSupplier clockMillis;
    private final Sleeper sleeper;
    private Long lastRequestAt;
    private long blockedUntil;

    public Throttle() {
        this(System::currentTimeMillis, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DiscogsException("Interrupted while waiting for the rate limit", e);
            }
        });
    }

    public Throttle(LongSupplier clockMillis, Sleeper sleeper) {
        this.clockMillis = clockMillis;
        this.sleeper = sleeper;
    }

    public synchronized void beforeRequest() {
        long now = clockMillis.getAsLong();
        long wait = Math.max(blockedUntil - now, 0);
        if (lastRequestAt != null) {
            wait = Math.max(wait, lastRequestAt + MIN_INTERVAL_MS - now);
        }
        if (wait > 0) {
            sleeper.sleep(wait);
        }
        lastRequestAt = clockMillis.getAsLong();
    }

    /** Call with the x-discogs-ratelimit-remaining header value, if present. */
    public synchronized void afterResponse(Integer remaining) {
        if (remaining != null && remaining <= 0) {
            blockFor();
        }
    }

    public synchronized void blockFor() {
        blockedUntil = clockMillis.getAsLong() + BLOCK_MS;
    }
}
