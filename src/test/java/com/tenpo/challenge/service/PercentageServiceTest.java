package com.tenpo.challenge.service;

import com.tenpo.challenge.cache.CachedPercentage;
import com.tenpo.challenge.cache.PercentageCache;
import com.tenpo.challenge.client.PercentageClient;
import com.tenpo.challenge.config.PercentageCacheProperties;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PercentageServiceTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Instant FETCHED_AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final CachedPercentage CACHED = new CachedPercentage(new BigDecimal("10.00"), FETCHED_AT);
    private static final BigDecimal FETCHED = new BigDecimal("15.00");
    private static final ResourceAccessException FAILURE = new ResourceAccessException("Connection refused");

    private final InMemoryPercentageCache cache = new InMemoryPercentageCache();
    private final FakePercentageClient client = new FakePercentageClient();
    private final MutableClock clock = new MutableClock(FETCHED_AT);
    private final PercentageService service =
            new PercentageService(client, cache, clock, new PercentageCacheProperties(TTL));

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
    void fallsBackToAStaleCachedPercentageWhenTheClientFails() {
        cache.save(CACHED);
        clock.set(FETCHED_AT.plus(Duration.ofHours(5)));
        client.respondWith(Try.failure(FAILURE));

        assertThat(service.getPercentage().get()).isEqualTo(CACHED.value());
        assertThat(client.calls).isOne();
        assertThat(cache.stored).contains(CACHED);
    }

    @Test
    void failsWhenTheClientFailsAndNothingIsCached() {
        client.respondWith(Try.failure(FAILURE));

        Try<BigDecimal> result = service.getPercentage();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getCause()).isSameAs(FAILURE);
        assertThat(cache.stored).isEmpty();
    }

    @Test
    void cachesTheFetchedPercentageWhenNothingIsCached() {
        client.respondWith(Try.success(FETCHED));

        assertThat(service.getPercentage().get()).isEqualTo(FETCHED);
        assertThat(client.calls).isOne();
        assertThat(cache.stored).contains(new CachedPercentage(FETCHED, FETCHED_AT));
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

    private static final class FakePercentageClient implements PercentageClient {

        private Try<BigDecimal> response = Try.failure(new IllegalStateException("No response configured"));
        private int calls;

        void respondWith(Try<BigDecimal> response) {
            this.response = response;
        }

        @Override
        public Try<BigDecimal> fetchPercentage() {
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
