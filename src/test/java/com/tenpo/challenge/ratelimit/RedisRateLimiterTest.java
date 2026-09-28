package com.tenpo.challenge.ratelimit;

import com.tenpo.challenge.ratelimit.RateLimitDecision.Allowed;
import com.tenpo.challenge.ratelimit.RateLimitDecision.Rejected;
import com.tenpo.challenge.support.RedisContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.tenpo.challenge.ratelimit.RedisRateLimiter.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterTest extends RedisContainerTest {

    private static final int MAX_REQUESTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        connectionFactory = containerConnectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.delete(KEY);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void allowsUpToTheLimitAndRejectsTheNextRequest() {
        RateLimiter limiter = limiter(MAX_REQUESTS, WINDOW);

        List<RateLimitDecision> allowed = IntStream.range(0, MAX_REQUESTS).mapToObj(i -> limiter.tryAcquire()).toList();
        RateLimitDecision fourth = limiter.tryAcquire();

        assertThat(allowed).allMatch(Allowed.class::isInstance);
        assertThat(fourth).isInstanceOfSatisfying(Rejected.class, rejected -> assertThat(rejected.retryAfter())
                .isBetween(Duration.ofSeconds(1), WINDOW)
                .satisfies(retryAfter -> assertThat(retryAfter.toNanosPart()).isZero()));
    }

    @Test
    void rejectedRequestsDoNotTakeASlot() {
        RateLimiter limiter = limiter(MAX_REQUESTS, WINDOW);
        IntStream.range(0, MAX_REQUESTS + 5).forEach(i -> limiter.tryAcquire());

        assertThat(redisTemplate.opsForZSet().zCard(KEY)).isEqualTo(MAX_REQUESTS);
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() throws InterruptedException {
        Duration shortWindow = Duration.ofSeconds(1);
        RateLimiter limiter = limiter(MAX_REQUESTS, shortWindow);
        IntStream.range(0, MAX_REQUESTS).forEach(i -> limiter.tryAcquire());
        assertThat(limiter.tryAcquire()).isInstanceOf(Rejected.class);

        Thread.sleep(shortWindow.plusMillis(200));

        assertThat(limiter.tryAcquire()).isInstanceOf(Allowed.class);
    }

    @Test
    void enforcesOneCombinedLimitAcrossInstancesSharingRedis() {
        LettuceConnectionFactory otherConnectionFactory = containerConnectionFactory();
        try {
            RateLimiter replicaA = limiter(MAX_REQUESTS, WINDOW);
            RateLimiter replicaB = new RedisRateLimiter(new StringRedisTemplate(otherConnectionFactory),
                    new RateLimitProperties(MAX_REQUESTS, WINDOW));

            assertThat(replicaA.tryAcquire()).isInstanceOf(Allowed.class);
            assertThat(replicaB.tryAcquire()).isInstanceOf(Allowed.class);
            assertThat(replicaA.tryAcquire()).isInstanceOf(Allowed.class);
            assertThat(replicaB.tryAcquire()).isInstanceOf(Rejected.class);
            assertThat(replicaA.tryAcquire()).isInstanceOf(Rejected.class);
        } finally {
            otherConnectionFactory.destroy();
        }
    }

    @Test
    void allowsExactlyTheLimitUnderConcurrentRequests() throws Exception {
        List<RateLimitDecision> decisions = fireConcurrently(limiter(MAX_REQUESTS, WINDOW), 20);

        assertThat(decisions).filteredOn(Allowed.class::isInstance).hasSize(MAX_REQUESTS);
        assertThat(decisions).filteredOn(Rejected.class::isInstance).hasSize(20 - MAX_REQUESTS);
    }

    @Test
    void keepsOneEntryPerAcceptedRequestEvenWhenTheyArriveTogether() throws Exception {
        List<RateLimitDecision> decisions = fireConcurrently(limiter(50, WINDOW), 20);

        assertThat(decisions).allMatch(Allowed.class::isInstance);
        assertThat(redisTemplate.opsForZSet().zCard(KEY)).isEqualTo(20);
    }

    @Test
    void givesTheKeyAnExpiryEqualToTheWindow() {
        limiter(MAX_REQUESTS, WINDOW).tryAcquire();

        Long expiryMillis = redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS);

        assertThat(expiryMillis).isPositive().isLessThanOrEqualTo(WINDOW.toMillis());
    }

    @Test
    void letsTheRequestThroughWhenRedisIsUnavailable() throws IOException {
        LettuceConnectionFactory unavailable = unreachableConnectionFactory();
        try {
            RateLimiter limiter = new RedisRateLimiter(new StringRedisTemplate(unavailable),
                    new RateLimitProperties(MAX_REQUESTS, WINDOW));

            assertThatNoException().isThrownBy(limiter::tryAcquire);
            assertThat(limiter.tryAcquire()).isInstanceOf(Allowed.class);
        } finally {
            unavailable.destroy();
        }
    }

    @Test
    void roundsTheRetryTimeUpToWholeSecondsAndAtLeastOne() {
        assertThat(RedisRateLimiter.wholeSecondsAtLeastOne(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(RedisRateLimiter.wholeSecondsAtLeastOne(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(RedisRateLimiter.wholeSecondsAtLeastOne(1_000_000)).isEqualTo(Duration.ofSeconds(1));
        assertThat(RedisRateLimiter.wholeSecondsAtLeastOne(1_000_001)).isEqualTo(Duration.ofSeconds(2));
        assertThat(RedisRateLimiter.wholeSecondsAtLeastOne(59_999_999)).isEqualTo(Duration.ofSeconds(60));
    }

    private RateLimiter limiter(int maxRequests, Duration window) {
        return new RedisRateLimiter(redisTemplate, new RateLimitProperties(maxRequests, window));
    }

    /**
     * Fires the requests from separate threads, released together by a latch.
     */
    private static List<RateLimitDecision> fireConcurrently(RateLimiter limiter, int requests) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            List<Future<RateLimitDecision>> futures = IntStream.range(0, requests)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return limiter.tryAcquire();
                    }))
                    .toList();
            start.countDown();
            return futures.stream().map(RedisRateLimiterTest::await).toList();
        }
    }

    private static RateLimitDecision await(Future<RateLimitDecision> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
