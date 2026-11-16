package com.shoppulse.ai;

import java.util.function.LongSupplier;

/**
 * A tiny thread-safe token bucket so a demo cannot burn through an API budget. Per instance and
 * global on purpose: the point is to cap spend, not to be fair between users.
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoClock;
    private double tokens;
    private long last;

    public TokenBucket(int capacity, int refillPerMinute) {
        this(capacity, refillPerMinute, System::nanoTime);
    }

    TokenBucket(int capacity, int refillPerMinute, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerNano = refillPerMinute / 60_000_000_000.0;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.last = nanoClock.getAsLong();
    }

    /** Takes one token if available. */
    public synchronized boolean tryAcquire() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - last) * refillPerNano);
        last = now;
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
}
