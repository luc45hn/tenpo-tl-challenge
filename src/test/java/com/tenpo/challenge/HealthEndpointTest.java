package com.tenpo.challenge;

import com.tenpo.challenge.support.RedisContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole application on a real port, with Postgres and Redis in containers: the health
 * endpoint reports UP and, living outside /api/v1, is neither rate limited nor recorded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class HealthEndpointTest extends RedisContainerTest {

    // Keep in sync with docker-compose.yml
    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Started and stopped by the Testcontainers JUnit extension
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    private static final String RATE_LIMIT_KEY = "ratelimit:requests";
    private static final Duration RECORDING_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @BeforeEach
    void cleanState() {
        redisTemplate.delete(RATE_LIMIT_KEY);
        jdbcClient.sql("DELETE FROM call_history").update();
    }

    @Test
    void reportsUpWhenPostgresAndRedisAreUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void isNeitherRateLimitedNorRecorded() throws Exception {
        List<Integer> healthStatuses = IntStream.range(0, 10).mapToObj(i -> statusOf("/actuator/health")).toList();

        // A call to the public API as a control: it is counted and recorded, and since the history
        // executor inserts in order, once it is recorded any earlier record would be too
        assertThat(get("/api/v1/calculate?num1=abc&num2=1").statusCode()).isEqualTo(400);
        awaitRecordedCalls(1);

        assertThat(healthStatuses).containsOnly(200);
        assertThat(jdbcClient.sql("SELECT path FROM call_history").query(String.class).list())
                .containsExactly("/api/v1/calculate");
        assertThat(redisTemplate.opsForZSet().zCard(RATE_LIMIT_KEY)).isEqualTo(1);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private int statusOf(String path) {
        try {
            return get(path).statusCode();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void awaitRecordedCalls(long expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(RECORDING_TIMEOUT);
        while (recordedCalls() < expected && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(recordedCalls()).as("recorded calls").isEqualTo(expected);
    }

    private long recordedCalls() {
        return jdbcClient.sql("SELECT count(*) FROM call_history").query(Long.class).single();
    }
}
