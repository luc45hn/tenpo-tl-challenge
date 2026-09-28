package com.tenpo.challenge.ratelimit;

/**
 * Global limit on the number of requests within a time window.
 */
public interface RateLimiter {

    /**
     * Tries to take a slot for one request. Never throws.
     */
    RateLimitDecision tryAcquire();
}
