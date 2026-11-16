package com.shoppulse.ai;

/** Thrown when the per-instance question budget is used up; mapped to HTTP 429. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
