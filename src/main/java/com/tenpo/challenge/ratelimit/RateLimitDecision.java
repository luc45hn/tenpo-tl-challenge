package com.tenpo.challenge.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of trying to take a slot from a {@link RateLimiter}.
 */
public sealed interface RateLimitDecision {

    Allowed ALLOWED = new Allowed();

    /**
     * The request may proceed.
     */
    record Allowed() implements RateLimitDecision {
    }

    /**
     * The limit is reached.
     *
     * @param retryAfter time until a slot frees up, in whole seconds and at least 1
     */
    record Rejected(Duration retryAfter) implements RateLimitDecision {

        public Rejected {
            Objects.requireNonNull(retryAfter, "retryAfter must not be null");
        }
    }
}
