package com.tenpo.challenge.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * With Postgres unreachable, getting a connection (what the history endpoint and the call history
 * recorder do) fails after the connection timeout configured in application.yml, not after
 * HikariCP's 30 second default. Uses the real configuration with only the datasource, pointed at
 * a local port where nothing listens.
 * <p>
 * The pool is started without its one-off startup check ({@code initialization-fail-timeout: -1}),
 * which would otherwise fail on the first connection attempt without waiting. This reproduces
 * Postgres going down after the application started: callers wait for the connection timeout.
 */
@SpringBootTest(classes = DataSourceFailFastTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.hikari.initialization-fail-timeout=-1")
class DataSourceFailFastTest {

    private static final Duration HIKARI_DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FAIL_FAST_BOUND = Duration.ofSeconds(10);

    @Autowired
    private HikariDataSource dataSource;

    @DynamicPropertySource
    static void unreachablePostgres(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + closedPort + "/challenge");
    }

    @Test
    void usesAConnectionTimeoutWellBelowTheHikariDefault() {
        assertThat(Duration.ofMillis(dataSource.getConnectionTimeout()))
                .isPositive()
                .isLessThan(FAIL_FAST_BOUND)
                .isLessThan(HIKARI_DEFAULT_TIMEOUT);
    }

    @Test
    void failsFastWhenPostgresIsUnreachable() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> {
            try (Connection ignored = dataSource.getConnection()) {
                // never reached
            }
        }).isInstanceOf(SQLTransientConnectionException.class)
                .hasMessageContaining("request timed out");

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isLessThan(FAIL_FAST_BOUND);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            FlywayAutoConfiguration.class,
            DataJdbcRepositoriesAutoConfiguration.class,
            DataRedisAutoConfiguration.class
    })
    static class TestApplication {
    }
}
