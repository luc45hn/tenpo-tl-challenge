package com.tenpo.challenge.cache;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A percentage obtained from the external service, together with the instant it was fetched.
 */
public record CachedPercentage(BigDecimal value, Instant fetchedAt) {

    public CachedPercentage {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
    }

    /**
     * Pure function: whether this entry is still fresh at {@code now}. An entry stops being fresh
     * exactly when {@code ttl} has elapsed since it was fetched.
     */
    public boolean isFresh(Instant now, Duration ttl) {
        return now.isBefore(fetchedAt.plus(ttl));
    }
}
