package com.tenpo.challenge.retry;

import io.vavr.control.Try;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RetrierTest {

    private static final Duration DELAY = Duration.ofMillis(200);
    private static final Predicate<Throwable> ALWAYS_RETRY = failure -> true;

    private final List<Duration> pauses = new ArrayList<>();
    private final Pause recordingPause = pauses::add;

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void returnsTheFirstSuccessWithoutPausing() {
        ScriptedOperation operation = new ScriptedOperation(Try.success("ok"));

        Try<String> result = retrier(3, ALWAYS_RETRY).execute(operation);

        assertThat(result.get()).isEqualTo("ok");
        assertThat(operation.calls).isOne();
        assertThat(pauses).isEmpty();
    }

    @Test
    void retriesAfterAFailureUntilItSucceeds() {
        ScriptedOperation operation = new ScriptedOperation(failure("first"), Try.success("ok"));

        Try<String> result = retrier(3, ALWAYS_RETRY).execute(operation);

        assertThat(result.get()).isEqualTo("ok");
        assertThat(operation.calls).isEqualTo(2);
        assertThat(pauses).containsExactly(DELAY);
    }

    @Test
    void returnsTheLastFailureWhenEveryAttemptFails() {
        Try<String> last = failure("third");
        ScriptedOperation operation = new ScriptedOperation(failure("first"), failure("second"), last);

        Try<String> result = retrier(3, ALWAYS_RETRY).execute(operation);

        assertThat(result.getCause()).isSameAs(last.getCause());
        assertThat(operation.calls).isEqualTo(3);
        assertThat(pauses).containsExactly(DELAY, DELAY);
    }

    @Test
    void doesNotRetryANonRetryableFailure() {
        Try<String> nonRetryable = failure("bad request");
        ScriptedOperation operation = new ScriptedOperation(nonRetryable, Try.success("ok"));

        Try<String> result = retrier(3, failure -> false).execute(operation);

        assertThat(result.getCause()).isSameAs(nonRetryable.getCause());
        assertThat(operation.calls).isOne();
        assertThat(pauses).isEmpty();
    }

    @Test
    void stopsRetryingOnceAFailureIsNotRetryable() {
        Try<String> nonRetryable = Try.failure(new IllegalArgumentException("bad request"));
        ScriptedOperation operation = new ScriptedOperation(failure("transient"), nonRetryable, Try.success("ok"));

        Try<String> result = retrier(3, IllegalStateException.class::isInstance).execute(operation);

        assertThat(result.getCause()).isSameAs(nonRetryable.getCause());
        assertThat(operation.calls).isEqualTo(2);
        assertThat(pauses).containsExactly(DELAY);
    }

    @Test
    void makesASingleAttemptWhenMaxAttemptsIsOne() {
        Try<String> only = failure("only");
        ScriptedOperation operation = new ScriptedOperation(only, Try.success("ok"));

        Try<String> result = retrier(1, ALWAYS_RETRY).execute(operation);

        assertThat(result.getCause()).isSameAs(only.getCause());
        assertThat(operation.calls).isOne();
        assertThat(pauses).isEmpty();
    }

    @Test
    void turnsAnOperationThatThrowsIntoAFailure() {
        IllegalStateException thrown = new IllegalStateException("boom");
        Supplier<Try<String>> throwing = () -> {
            throw thrown;
        };

        Try<String> result = retrier(1, ALWAYS_RETRY).execute(throwing);

        assertThat(result.getCause()).isSameAs(thrown);
    }

    @Test
    void stopsRetryingWhenThePauseIsInterrupted() {
        Try<String> first = failure("first");
        ScriptedOperation operation = new ScriptedOperation(first, Try.success("ok"));
        Pause interrupted = duration -> {
            throw new InterruptedException();
        };

        Try<String> result = new Retrier(3, ALWAYS_RETRY, DELAY, interrupted).execute(operation);

        assertThat(result.getCause()).isSameAs(first.getCause());
        assertThat(operation.calls).isOne();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void turnsAnInterruptedExceptionRethrownByVavrIntoAFailureWithoutRetrying() {
        AtomicInteger calls = new AtomicInteger();
        // Vavr treats InterruptedException as fatal: Try.of rethrows it instead of capturing it
        Supplier<Try<String>> interruptedOperation = () -> {
            calls.incrementAndGet();
            return Try.of(() -> {
                throw new InterruptedException();
            });
        };

        Try<String> result = retrier(3, ALWAYS_RETRY).execute(interruptedOperation);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(calls).hasValue(1);
        assertThat(pauses).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void doesNotRetryWhenTheOperationFailsBecauseTheThreadWasInterrupted() {
        AtomicInteger calls = new AtomicInteger();
        Try<String> interruptedFailure = failure("request was interrupted");
        // Like Spring's JDK HTTP client: restores the interrupt flag and reports an I/O failure
        Supplier<Try<String>> interruptedOperation = () -> {
            calls.incrementAndGet();
            Thread.currentThread().interrupt();
            return interruptedFailure;
        };

        Try<String> result = retrier(3, ALWAYS_RETRY).execute(interruptedOperation);

        assertThat(result.getCause()).isSameAs(interruptedFailure.getCause());
        assertThat(calls).hasValue(1);
        assertThat(pauses).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Retrier(0, ALWAYS_RETRY, DELAY, recordingPause));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Retrier(3, ALWAYS_RETRY, Duration.ofMillis(-1), recordingPause));
    }

    private Retrier retrier(int maxAttempts, Predicate<Throwable> retryable) {
        return new Retrier(maxAttempts, retryable, DELAY, recordingPause);
    }

    private static Try<String> failure(String message) {
        return Try.failure(new IllegalStateException(message));
    }

    /**
     * Returns the scripted outcomes in order, one per call.
     */
    private static final class ScriptedOperation implements Supplier<Try<String>> {

        private final List<Try<String>> outcomes;
        private int calls;

        @SafeVarargs
        ScriptedOperation(Try<String>... outcomes) {
            this.outcomes = List.of(outcomes);
        }

        @Override
        public Try<String> get() {
            return outcomes.get(calls++);
        }
    }
}
