package com.tenpo.challenge.service;

import com.tenpo.challenge.cache.CachedPercentage;
import com.tenpo.challenge.cache.PercentageCache;
import com.tenpo.challenge.client.PercentageClient;
import com.tenpo.challenge.config.PercentageCacheProperties;
import com.tenpo.challenge.retry.Retrier;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;

/**
 * Resolves the percentage to apply. Holds the effects around the external call.
 */
@Service
public class PercentageService {

    private static final Logger log = LoggerFactory.getLogger(PercentageService.class);

    private final PercentageClient percentageClient;
    private final PercentageCache percentageCache;
    private final Retrier retrier;
    private final Clock clock;
    private final Duration ttl;

    public PercentageService(PercentageClient percentageClient, PercentageCache percentageCache, Retrier retrier,
                             Clock clock, PercentageCacheProperties cacheProperties) {
        this.percentageClient = percentageClient;
        this.percentageCache = percentageCache;
        this.retrier = retrier;
        this.clock = clock;
        this.ttl = cacheProperties.ttl();
    }

    /**
     * Returns the cached percentage while it is fresh. Otherwise fetches it from the external
     * service, retrying transient failures, and caches it; if that fails, falls back to the cached
     * percentage even if stale, and only fails when there is none.
     */
    public Try<BigDecimal> getPercentage() {
        Option<CachedPercentage> cached = percentageCache.find();
        return cached
                .filter(entry -> entry.isFresh(clock.instant(), ttl))
                .map(entry -> Try.success(entry.value()))
                .getOrElse(() -> fetchAndCache().recoverWith(failure -> fallBackTo(cached, failure)));
    }

    private Try<BigDecimal> fetchAndCache() {
        return retrier.execute(percentageClient::fetchPercentage)
                .peek(value -> percentageCache.save(new CachedPercentage(value, clock.instant())));
    }

    private static Try<BigDecimal> fallBackTo(Option<CachedPercentage> cached, Throwable failure) {
        return cached
                .peek(entry -> log.warn("Percentage service failed ({}); using last known percentage fetched at {}",
                        failure.toString(), entry.fetchedAt()))
                .map(CachedPercentage::value)
                .toTry(() -> failure);
    }
}
