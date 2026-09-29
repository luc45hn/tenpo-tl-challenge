package com.tenpo.challenge.exception;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Framework-free helpers to report failures in logs.
 */
public final class Failures {

    private Failures() {
    }

    /**
     * Summarizes a failure in one line, the exception and its root cause, without a stack trace:
     * e.g. {@code RedisConnectionFailureException: Unable to connect (cause: ConnectException: Connection refused)}.
     */
    public static String describe(Throwable failure) {
        Throwable rootCause = Stream.iterate(failure, Objects::nonNull, Throwable::getCause)
                .reduce((cause, next) -> next)
                .orElse(failure);
        return rootCause == failure ? failure.toString() : failure + " (cause: " + rootCause + ")";
    }
}
