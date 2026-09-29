package com.tenpo.challenge.util;

import java.time.Duration;
import java.util.List;

/**
 * Framework-free formatting of durations for user-facing text.
 */
public final class Durations {

    private record Unit(Duration length, String name) {
    }

    private static final List<Unit> UNITS = List.of(
            new Unit(Duration.ofDays(1), "day"),
            new Unit(Duration.ofHours(1), "hour"),
            new Unit(Duration.ofMinutes(1), "minute"),
            new Unit(Duration.ofSeconds(1), "second"),
            new Unit(Duration.ofMillis(1), "millisecond"));

    private Durations() {
    }

    /**
     * A duration in its largest whole unit, e.g. "30 minutes", "1 minute", "200 milliseconds".
     * Durations that are not a whole number of milliseconds fall back to ISO-8601.
     */
    public static String quantity(Duration duration) {
        return UNITS.stream()
                .filter(unit -> duration.toNanos() % unit.length().toNanos() == 0)
                .findFirst()
                .map(unit -> {
                    long amount = duration.toNanos() / unit.length().toNanos();
                    return amount + " " + (amount == 1 ? unit.name() : unit.name() + "s");
                })
                .orElseGet(duration::toString);
    }

    /**
     * A duration after "per", e.g. "minute" (not "1 minute") or "30 seconds".
     */
    public static String per(Duration duration) {
        String quantity = quantity(duration);
        return quantity.startsWith("1 ") ? quantity.substring(2) : quantity;
    }
}
