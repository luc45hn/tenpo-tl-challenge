package com.tenpo.challenge.repository;

import com.tenpo.challenge.model.CallHistory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Testcontainers(disabledWithoutDocker = true)
class CallHistoryRepositoryTest {

    // Keep in sync with docker-compose.yml
    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Started and stopped by the Testcontainers JUnit extension
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("calledAt"), Sort.Order.desc("id"));

    @Autowired
    private CallHistoryRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void appliesTheMigration() {
        List<String> columns = jdbcClient.sql("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_name = 'call_history' ORDER BY ordinal_position""")
                .query(String.class)
                .list();
        Boolean indexExists = jdbcClient.sql(
                        "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_call_history_called_at')")
                .query(Boolean.class)
                .single();

        assertThat(columns).containsExactly(
                "id", "called_at", "method", "path", "query_string", "status", "response_body");
        assertThat(indexExists).isTrue();
    }

    @Test
    void findsWhatWasSaved() {
        CallHistory saved = repository.save(CallHistory.of(
                BASE, "GET", "/api/v1/calculate", "num1=5&num2=5", 200, "{\"result\":11.00}"));

        assertThat(saved.id()).isNotNull();
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void storesNullQueryStringAndBody() {
        CallHistory saved = repository.save(CallHistory.of(BASE, "GET", "/api/v1/calculate", null, 500, null));

        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void preservesTheInstantAcrossTimeZones() {
        Instant calledAt = Instant.parse("2026-06-30T23:59:59.123456Z");

        CallHistory saved = repository.save(CallHistory.of(calledAt, "GET", "/api/v1/calculate", null, 200, "{}"));

        assertThat(repository.findById(saved.id()).orElseThrow().calledAt()).isEqualTo(calledAt);
    }

    @Test
    void paginatesNewestFirstWithCorrectBoundaries() {
        List<CallHistory> saved = IntStream.range(0, 5)
                .mapToObj(minute -> repository.save(CallHistory.of(
                        BASE.plus(Duration.ofMinutes(minute)), "GET", "/api/v1/calculate", "call=" + minute, 200, "{}")))
                .toList();

        Page<CallHistory> first = repository.findAll(PageRequest.of(0, 2, NEWEST_FIRST));
        Page<CallHistory> second = repository.findAll(PageRequest.of(1, 2, NEWEST_FIRST));
        Page<CallHistory> last = repository.findAll(PageRequest.of(2, 2, NEWEST_FIRST));
        Page<CallHistory> beyond = repository.findAll(PageRequest.of(3, 2, NEWEST_FIRST));

        assertThat(first.getContent()).containsExactly(saved.get(4), saved.get(3));
        assertThat(second.getContent()).containsExactly(saved.get(2), saved.get(1));
        assertThat(last.getContent()).containsExactly(saved.get(0));
        assertThat(beyond.getContent()).isEmpty();
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(last.isLast()).isTrue();
    }
}
