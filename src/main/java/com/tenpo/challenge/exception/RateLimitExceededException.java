package com.tenpo.challenge.exception;

import com.tenpo.challenge.util.Durations;

import java.time.Duration;

/**
 * Raised when a request exceeds the global rate limit.
 */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    /**
     * @param maxRequests configured maximum number of requests per window
     * @param window      configured window
     * @param retryAfter  time until a slot frees up, in whole seconds
     */
    public RateLimitExceededException(int maxRequests, Duration window, Duration retryAfter) {
        super("Rate limit exceeded: at most %s per %s. Try again in %s.".formatted(
                quantity(maxRequests, "request"), Durations.per(window), quantity(retryAfter.toSeconds(), "second")));
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return retryAfter.toSeconds();
    }

    private static String quantity(long amount, String singular) {
        return amount + " " + (amount == 1 ? singular : singular + "s");
    }
}
