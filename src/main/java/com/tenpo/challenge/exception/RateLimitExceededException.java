package com.tenpo.challenge.exception;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

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
                quantity(maxRequests, "request"), describe(window), quantity(retryAfter.toSeconds(), "second")));
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return retryAfter.toSeconds();
    }

    /**
     * Describes the window in its largest whole unit: "minute", "30 seconds", "2 hours"...
     */
    private static String describe(Duration window) {
        return Stream.of(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS, ChronoUnit.MILLIS)
                .filter(unit -> window.toNanos() % unit.getDuration().toNanos() == 0)
                .findFirst()
                .map(unit -> {
                    long amount = window.toNanos() / unit.getDuration().toNanos();
                    String name = singularName(unit);
                    return amount == 1 ? name : quantity(amount, name);
                })
                .orElse(window.toString());
    }

    private static String singularName(ChronoUnit unit) {
        return unit == ChronoUnit.MILLIS ? "millisecond" : unit.name().toLowerCase().replaceFirst("s$", "");
    }

    private static String quantity(long amount, String singular) {
        return amount + " " + (amount == 1 ? singular : singular + "s");
    }
}
