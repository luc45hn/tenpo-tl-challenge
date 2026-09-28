package com.tenpo.challenge.cache;

import io.vavr.control.Option;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;

import static com.tenpo.challenge.cache.RedisPercentageCache.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@Testcontainers(disabledWithoutDocker = true)
class RedisPercentageCacheTest {

    // Keep in sync with docker-compose.yml
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8.10.2-alpine");
    private static final int REDIS_PORT = 6379;
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(REDIS_PORT);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private PercentageCache cache;

    @BeforeEach
    void setUp() {
        connectionFactory = connectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.delete(KEY);
        cache = new RedisPercentageCache(redisTemplate, jsonMapper);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void findsWhatWasSaved() {
        CachedPercentage entry = new CachedPercentage(new BigDecimal("12.34"), Instant.parse("2026-01-01T12:00:00Z"));

        cache.save(entry);

        assertThat(cache.find()).containsExactly(entry);
    }

    @Test
    void storesAReadableJsonValueWithoutExpiry() {
        cache.save(new CachedPercentage(new BigDecimal("12.30"), Instant.parse("2026-01-01T12:00:00Z")));

        assertThat(redisTemplate.opsForValue().get(KEY))
                .isEqualTo("{\"value\":12.30,\"fetchedAt\":\"2026-01-01T12:00:00Z\"}");
        assertThat(redisTemplate.getExpire(KEY)).isEqualTo(-1L);
    }

    @Test
    void savingReplacesThePreviousEntry() {
        cache.save(new CachedPercentage(new BigDecimal("5.00"), Instant.parse("2026-01-01T12:00:00Z")));
        CachedPercentage latest = new CachedPercentage(new BigDecimal("20.00"), Instant.parse("2026-01-01T13:00:00Z"));

        cache.save(latest);

        assertThat(cache.find()).containsExactly(latest);
    }

    @Test
    void findsNothingWhenTheKeyIsEmpty() {
        assertThat(cache.find()).isEmpty();
    }

    @Test
    void treatsInvalidJsonAsAbsent() {
        redisTemplate.opsForValue().set(KEY, "not json");

        assertThat(cache.find()).isEmpty();
    }

    @Test
    void treatsNonNumericValueAsAbsent() {
        redisTemplate.opsForValue().set(KEY, "{\"value\":\"abc\",\"fetchedAt\":\"2026-01-01T12:00:00Z\"}");

        assertThat(cache.find()).isEmpty();
    }

    @Test
    void treatsMissingFieldsAsAbsent() {
        redisTemplate.opsForValue().set(KEY, "{\"value\":12.34}");

        assertThat(cache.find()).isEmpty();
    }

    @Test
    void treatsAValueOfAnotherRedisTypeAsAbsentAndOverwritesIt() {
        redisTemplate.opsForList().leftPush(KEY, "12.34");
        CachedPercentage entry = new CachedPercentage(new BigDecimal("12.34"), Instant.parse("2026-01-01T12:00:00Z"));

        assertThat(cache.find()).isEmpty();

        cache.save(entry);

        assertThat(cache.find()).containsExactly(entry);
    }

    @Test
    void degradesGracefullyWhenRedisIsUnavailable() throws IOException {
        LettuceConnectionFactory unavailable = connectionFactory("localhost", unusedLocalPort());
        try {
            PercentageCache unavailableCache = new RedisPercentageCache(new StringRedisTemplate(unavailable), jsonMapper);

            assertThat(unavailableCache.find()).isEqualTo(Option.none());
            assertThatNoException().isThrownBy(() -> unavailableCache.save(
                    new CachedPercentage(BigDecimal.TEN, Instant.parse("2026-01-01T12:00:00Z"))));
        } finally {
            unavailable.destroy();
        }
    }

    private static LettuceConnectionFactory connectionFactory(String host, int port) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(TIMEOUT)
                .build();
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        factory.afterPropertiesSet();
        return factory;
    }

    private static int unusedLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
