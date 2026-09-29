package com.tenpo.challenge.ratelimit;

import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.tenpo.challenge.exception.Failures.describe;

/**
 * Sliding window log in Redis: a sorted set with one entry per accepted request, checked and
 * updated atomically by a Lua script, so the limit holds across replicas. If Redis fails the
 * request is let through (availability over strictness).
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    static final String KEY = "ratelimit:requests";

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final RedisScript<List<Long>> SLIDING_WINDOW_SCRIPT = (RedisScript) RedisScript.of(
            new ClassPathResource("ratelimit/sliding-window.lua"), List.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final Duration window;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = properties.maxRequests();
        this.window = properties.window();
    }

    @Override
    public RateLimitDecision tryAcquire() {
        return Try.of(() -> redisTemplate.execute(SLIDING_WINDOW_SCRIPT, List.of(KEY),
                        String.valueOf(maxRequests), String.valueOf(window.toMillis()), UUID.randomUUID().toString()))
                .mapTry(RedisRateLimiter::toDecision)
                .onFailure(e -> log.warn("Rate limiter unavailable, letting the request through: {}", describe(e)))
                .getOrElse(RateLimitDecision.ALLOWED);
    }

    /**
     * Reads the script's {@code {allowed, retryAfterMicros}} reply.
     */
    private static RateLimitDecision toDecision(List<Long> reply) {
        if (reply == null || reply.size() != 2) {
            throw new IllegalStateException("Unexpected rate limit script reply: " + reply);
        }
        return reply.get(0) == 1L
                ? RateLimitDecision.ALLOWED
                : new RateLimitDecision.Rejected(wholeSecondsAtLeastOne(reply.get(1)));
    }

    /**
     * Rounds up to whole seconds, at least 1, as sent in the Retry-After header.
     */
    static Duration wholeSecondsAtLeastOne(long micros) {
        long seconds = Math.ceilDiv(micros, 1_000_000L);
        return Duration.ofSeconds(Math.max(1, seconds));
    }
}
