package com.tenpo.challenge.cache;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CachedPercentageTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Instant FETCHED_AT = Instant.parse("2026-01-01T12:00:00Z");

    private final CachedPercentage entry = new CachedPercentage(new BigDecimal("12.34"), FETCHED_AT);

    @Test
    void isFreshRightAfterBeingFetched() {
        assertThat(entry.isFresh(FETCHED_AT, TTL)).isTrue();
    }

    @Test
    void isFreshJustBeforeTheTtlElapses() {
        assertThat(entry.isFresh(FETCHED_AT.plus(TTL).minusNanos(1), TTL)).isTrue();
    }

    @Test
    void isStaleExactlyWhenTheTtlElapses() {
        assertThat(entry.isFresh(FETCHED_AT.plus(TTL), TTL)).isFalse();
    }

    @Test
    void isStaleAfterTheTtlElapses() {
        assertThat(entry.isFresh(FETCHED_AT.plus(Duration.ofHours(2)), TTL)).isFalse();
    }

    @Test
    void rejectsMissingFields() {
        assertThatNullPointerException().isThrownBy(() -> new CachedPercentage(null, FETCHED_AT));
        assertThatNullPointerException().isThrownBy(() -> new CachedPercentage(BigDecimal.TEN, null));
    }
}
