package com.retailmedia.insights.security;

import java.util.function.LongSupplier;

/**
 * A thread-safe token bucket: allows bursts up to {@code capacity}, then a steady
 * {@code refillPerSecond}. The time source is injected so behaviour is testable without sleeping.
 */
public final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoTime;
    private double tokens;
    private long lastRefill;

    public TokenBucket(int capacity, double refillPerSecond, LongSupplier nanoTime) {
        if (capacity <= 0 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refillPerSecond must be positive");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / NANOS_PER_SECOND;
        this.nanoTime = nanoTime;
        this.tokens = capacity;
        this.lastRefill = nanoTime.getAsLong();
    }

    /** Takes one token if available. */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /** Whole seconds until a token is available (at least 1), for the Retry-After header. */
    public synchronized long secondsUntilNextToken() {
        refill();
        double missing = Math.max(0, 1 - tokens);
        return Math.max(1, (long) Math.ceil(missing / refillPerNano / NANOS_PER_SECOND));
    }

    private void refill() {
        long now = nanoTime.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
        lastRefill = now;
    }
}
