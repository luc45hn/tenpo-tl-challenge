package com.tenpo.challenge.cache;

import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static com.tenpo.challenge.exception.Failures.describe;

/**
 * Stores the percentage in Redis as a single JSON string, e.g.
 * {@code {"value":12.34,"fetchedAt":"2026-01-01T12:00:00Z"}}, with no Redis expiry: freshness is
 * decided by the application so the entry can still serve as the last known value.
 */
@Component
public class RedisPercentageCache implements PercentageCache {

    static final String KEY = "percentage:last";

    private static final Logger log = LoggerFactory.getLogger(RedisPercentageCache.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public RedisPercentageCache(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Option<CachedPercentage> find() {
        return Try.of(() -> redisTemplate.opsForValue().get(KEY))
                .onFailure(e -> log.warn("Could not read cached percentage from Redis: {}", describe(e)))
                .toOption()
                .flatMap(Option::of)
                .flatMap(this::deserialize);
    }

    @Override
    public void save(CachedPercentage percentage) {
        Try.run(() -> redisTemplate.opsForValue().set(KEY, jsonMapper.writeValueAsString(percentage)))
                .onFailure(e -> log.warn("Could not store percentage in Redis: {}", describe(e)));
    }

    private Option<CachedPercentage> deserialize(String json) {
        return Try.of(() -> jsonMapper.readValue(json, CachedPercentage.class))
                .onFailure(e -> log.warn("Ignoring malformed cached percentage: {}", describe(e)))
                .toOption();
    }
}
