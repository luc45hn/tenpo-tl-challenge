package com.tenpo.challenge.retry;

import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.tenpo.challenge.exception.Failures.describe;

/**
 * Retries a {@link Try}-returning operation: up to {@code maxAttempts} calls in total, pausing
 * {@code delay} between them, as long as each failure is accepted by the {@code retryable}
 * predicate. Stops at the first success, never pauses after the last attempt and never throws.
 * <p>
 * Interruption stops the retries. Vavr treats {@link InterruptedException} as fatal (a
 * {@code Try} rethrows it instead of capturing it), so it is handled with plain try/catch where
 * it can arise: while pausing, and when the operation itself throws it.
 */
public final class Retrier {

    private static final Logger log = LoggerFactory.getLogger(Retrier.class);

    private final int maxAttempts;
    private final Predicate<Throwable> retryable;
    private final Duration delay;
    private final Pause pause;

    public Retrier(int maxAttempts, Predicate<Throwable> retryable, Duration delay, Pause pause) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, but was " + maxAttempts);
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative, but was " + delay);
        }
        this.maxAttempts = maxAttempts;
        this.retryable = Objects.requireNonNull(retryable, "retryable must not be null");
        this.delay = delay;
        this.pause = Objects.requireNonNull(pause, "pause must not be null");
    }

    /**
     * Runs the operation until it succeeds, fails with a non-retryable failure, runs out of
     * attempts or is interrupted, returning the last outcome.
     */
    public <T> Try<T> execute(Supplier<Try<T>> operation) {
        return attempt(operation, 1);
    }

    private <T> Try<T> attempt(Supplier<Try<T>> operation, int attempt) {
        return invoke(operation)
                .recoverWith(failure -> shouldRetry(attempt, failure)
                        ? retryAfterPause(operation, attempt, failure)
                        : Try.failure(failure));
    }

    private boolean shouldRetry(int attempt, Throwable failure) {
        return attempt < maxAttempts
                && retryable.test(failure)
                && !Thread.currentThread().isInterrupted();
    }

    /**
     * Logs the failed attempt, pauses and tries again. An interrupted pause restores the
     * interrupt flag and returns the failure of the last attempt instead of retrying.
     */
    private <T> Try<T> retryAfterPause(Supplier<Try<T>> operation, int attempt, Throwable failure) {
        log.warn("Attempt {}/{} failed, retrying in {} ms: {}", attempt, maxAttempts, delay.toMillis(), describe(failure));
        try {
            pause.pause(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Try.failure(failure);
        }
        return attempt(operation, attempt + 1);
    }

    /**
     * Calls the operation, turning anything it throws into a failure. An {@link InterruptedException}
     * (which can only arrive sneakily thrown, e.g. rethrown by a nested Vavr {@code Try}) restores
     * the interrupt flag and is wrapped, since a Vavr failure cannot hold it directly.
     */
    private static <T> Try<T> invoke(Supplier<Try<T>> operation) {
        try {
            return Objects.requireNonNull(operation.get(), "operation returned null");
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return Try.failure(new IllegalStateException("Interrupted while running the operation", e));
            }
            return Try.failure(e);
        }
    }
}
