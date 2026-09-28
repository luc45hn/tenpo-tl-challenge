package com.tenpo.challenge.client;

import com.sun.net.httpserver.HttpServer;
import com.tenpo.challenge.config.PercentageClientConfig;
import com.tenpo.challenge.config.PercentageClientProperties;
import io.vavr.control.Try;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import static com.tenpo.challenge.client.RestPercentageClient.PERCENTAGE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the client over real HTTP against an in-process JDK HTTP server.
 */
class RestPercentageClientTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsThePercentageOnSuccess() {
        respondWith(200, "{\"percentage\":12.34}");

        Try<BigDecimal> result = clientFor(serverUri()).fetchPercentage();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isEqualTo(new BigDecimal("12.34"));
    }

    @Test
    void failsOnServerError() {
        respondWith(503, "{\"error\":\"unavailable\"}");

        assertThat(failureOf(clientFor(serverUri()).fetchPercentage()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void failsOnClientError() {
        respondWith(404, "");

        assertThat(failureOf(clientFor(serverUri()).fetchPercentage()))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void failsOnReadTimeout() {
        server.createContext(PERCENTAGE_PATH, exchange -> {
            sleep(READ_TIMEOUT.multipliedBy(3));
            exchange.close();
        });

        assertThat(failureOf(clientFor(serverUri()).fetchPercentage()))
                .isInstanceOf(ResourceAccessException.class)
                .hasRootCauseInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void failsWhenTheServerIsUnreachable() throws IOException {
        assertThat(failureOf(clientFor(unusedLocalUri()).fetchPercentage()))
                .isInstanceOf(ResourceAccessException.class)
                .hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    void failsOnMalformedJson() {
        respondWith(200, "not json");

        assertThat(failureOf(clientFor(serverUri()).fetchPercentage()))
                .isExactlyInstanceOf(RestClientException.class);
    }

    @Test
    void failsOnNonNumericPercentage() {
        respondWith(200, "{\"percentage\":\"abc\"}");

        assertThat(failureOf(clientFor(serverUri()).fetchPercentage()))
                .isExactlyInstanceOf(RestClientException.class);
    }

    @Test
    void failsWhenThePercentageIsMissing() {
        respondWith(200, "{\"value\":12.34}");

        assertThat(failureOf(clientFor(serverUri()).fetchPercentage()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnEmptyBody() {
        respondWith(200, "");

        assertThat(failureOf(clientFor(serverUri()).fetchPercentage()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWithoutThrowingWhenTheThreadIsInterrupted() {
        respondWith(200, "{\"percentage\":12.34}");
        PercentageClient client = clientFor(serverUri());
        Thread.currentThread().interrupt();
        try {
            assertThat(failureOf(client.fetchPercentage()))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasCauseInstanceOf(IOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static Throwable failureOf(Try<BigDecimal> result) {
        assertThat(result.isFailure()).as("expected a failed Try but got %s", result).isTrue();
        return result.getCause();
    }

    private void respondWith(int status, String body) {
        server.createContext(PERCENTAGE_PATH, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private URI serverUri() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    private static URI unusedLocalUri() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return URI.create("http://localhost:" + socket.getLocalPort());
        }
    }

    private static PercentageClient clientFor(URI baseUrl) {
        PercentageClientProperties properties = new PercentageClientProperties(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
        return new RestPercentageClient(new PercentageClientConfig().percentageRestClient(properties));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
