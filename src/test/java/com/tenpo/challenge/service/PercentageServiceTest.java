package com.tenpo.challenge.service;

import com.tenpo.challenge.cache.CachedPercentage;
import com.tenpo.challenge.cache.PercentageCache;
import com.tenpo.challenge.client.PercentageClient;
import com.tenpo.challenge.client.TransientFailures;
import com.tenpo.challenge.config.PercentageCacheProperties;
import com.tenpo.challenge.retry.Retrier;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PercentageServiceTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(200);
    private static final Instant FETCHED_AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final CachedPercentage CACHED = new CachedPercentage(new BigDecimal("10.00"), FETCHED_AT);
    private static final BigDecimal FETCHED = new BigDecimal("15.00");
    private static final ResourceAccessException TRANSIENT_FAILURE = new ResourceAccessException("Connection refused");
    private static final HttpClientErrorException NON_TRANSIENT_FAILURE =
            HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);

    private final InMemoryPercentageCache cache = new InMemoryPercentageCache();
    private final FakePercentageClient client = new FakePercentageClient();
    private final MutableClock clock = new MutableClock(FETCHED_AT);
    private final List<Duration> pauses = new ArrayList<>();
    private final Retrier retrier = new Retrier(MAX_ATTEMPTS, TransientFailures::isTransient, RETRY_DELAY, pauses::add);
    private final PercentageService service =
            new PercentageService(client, cache, retrier, clock, new PercentageCacheProperties(TTL));

    @Test
    void returnsAFreshCachedPercentageWithoutCallingTheClient() {
        cache.save(CACHED);
        clock.set(FETCHED_AT.plus(Duration.ofMinutes(10)));
        client.respondWith(Try.success(FETCHED));

        assertThat(service.getPercentage().get()).isEqualTo(CACHED.value());
        assertThat(client.calls).isZero();
        assertThat(cache.stored).contains(CACHED);
    }

    @Test
    void refreshesAStaleCachedPercentageWhenTheClientSucceeds() {
        cache.save(CACHED);
        Instant now = FETCHED_AT.plus(Duration.ofMinutes(31));
        clock.set(now);
        client.respondWith(Try.success(FETCHED));

        assertThat(service.getPercentage().get()).isEqualTo(FETCHED);
        assertThat(client.calls).isOne();
        assertThat(cache.stored).contains(new CachedPercentage(FETCHED, now));
    }

    @Test
    void cachesTheFetchedPercentageWhenNothingIsCached() {
        client.respondWith(Try.success(FETCHED));

        assertThat(service.getPercentage().get()).isEqualTo(FETCHED);
        assertThat(client.calls).isOne();
        assertThat(pauses).isEmpty();
        assertThat(cache.stored).contains(new CachedPercentage(FETCHED, FETCHED_AT));
    }

    @Test
    void retriesTransientFailuresUntilTheClientSucceeds() {
        client.respondWith(Try.failure(TRANSIENT_FAILURE), Try.failure(TRANSIENT_FAILURE), Try.success(FETCHED));

        assertThat(service.getPercentage().get()).isEqualTo(FETCHED);
        assertThat(client.calls).isEqualTo(3);
        assertThat(pauses).containsExactly(RETRY_DELAY, RETRY_DELAY);
        assertThat(cache.stored).contains(new CachedPercentage(FETCHED, FETCHED_AT));
    }

    @Test
    void fallsBackToAStaleCachedPercentageWhenEveryAttemptFails() {
        cache.save(CACHED);
        clock.set(FETCHED_AT.plus(Duration.ofHours(5)));
        client.respondWith(Try.failure(TRANSIENT_FAILURE));

        assertThat(service.getPercentage().get()).isEqualTo(CACHED.value());
        assertThat(client.calls).isEqualTo(MAX_ATTEMPTS);
        assertThat(pauses).hasSize(MAX_ATTEMPTS - 1);
        assertThat(cache.stored).contains(CACHED);
    }

    @Test
    void failsWhenEveryAttemptFailsAndNothingIsCached() {
        client.respondWith(Try.failure(TRANSIENT_FAILURE));

        Try<BigDecimal> result = service.getPercentage();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getCause()).isSameAs(TRANSIENT_FAILURE);
        assertThat(client.calls).isEqualTo(MAX_ATTEMPTS);
        assertThat(cache.stored).isEmpty();
    }

    @Test
    void doesNotRetryANonTransientFailure() {
        client.respondWith(Try.failure(NON_TRANSIENT_FAILURE), Try.success(FETCHED));

        Try<BigDecimal> result = service.getPercentage();

        assertThat(result.getCause()).isSameAs(NON_TRANSIENT_FAILURE);
        assertThat(client.calls).isOne();
        assertThat(pauses).isEmpty();
        assertThat(cache.stored).isEmpty();
    }

    @Test
    void usesTheCachedPercentageUntilJustBeforeTheTtlElapses() {
        cache.save(CACHED);
        clock.set(FETCHED_AT.plus(TTL).minusNanos(1));
        client.respondWith(Try.success(FETCHED));

        assertThat(service.getPercentage().get()).isEqualTo(CACHED.value());
        assertThat(client.calls).isZero();
    }

    @Test
    void callsTheClientExactlyWhenTheTtlElapses() {
        cache.save(CACHED);
        clock.set(FETCHED_AT.plus(TTL));
        client.respondWith(Try.success(FETCHED));

        assertThat(service.getPercentage().get()).isEqualTo(FETCHED);
        assertThat(client.calls).isOne();
    }

    private static final class InMemoryPercentageCache implements PercentageCache {

        private Option<CachedPercentage> stored = Option.none();

        @Override
        public Option<CachedPercentage> find() {
            return stored;
        }

        @Override
        public void save(CachedPercentage percentage) {
            stored = Option.some(percentage);
        }
    }

    /**
     * Returns the scripted responses in order, repeating the last one once they run out.
     */
    private static final class FakePercentageClient implements PercentageClient {

        private List<Try<BigDecimal>> responses = List.of(Try.failure(new IllegalStateException("No response configured")));
        private int calls;

        @SafeVarargs
        final void respondWith(Try<BigDecimal>... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public Try<BigDecimal> fetchPercentage() {
            Try<BigDecimal> response = responses.get(Math.min(calls, responses.size() - 1));
            calls++;
            return response;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
