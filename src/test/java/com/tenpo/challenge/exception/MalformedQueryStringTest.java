package com.tenpo.challenge.exception;

import com.tenpo.challenge.dto.CalculationRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An invalid percent-encoding in the query string only fails inside a real servlet container
 * (Tomcat parses the parameters; MockMvc does not), so this runs a minimal application on an
 * embedded Tomcat: two test-only controllers and the {@link GlobalExceptionHandler}, without
 * Redis or Postgres.
 */
@SpringBootTest(classes = MalformedQueryStringTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MalformedQueryStringTest {

    private static final String MALFORMED_MESSAGE =
            "The query string is malformed: a parameter contains an invalid percent-encoding.";

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"/test/model-attribute?num1=%zz&num2=1", "/test/request-param?page=%zz"})
    void anInvalidPercentEncodingIsABadRequest(String target) throws IOException {
        String response = rawGet(target);

        assertThat(response).startsWith("HTTP/1.1 400");
        assertThat(response).contains("Content-Type: application/problem+json");
        assertThat(response)
                .contains("\"title\":\"Invalid request parameters\"")
                .contains("\"detail\":\"" + MALFORMED_MESSAGE + "\"")
                .contains("\"status\":400");
        assertThat(response)
                .doesNotContain("InvalidParameterException")
                .doesNotContain("org.apache")
                .doesNotContain("%zz")
                .doesNotContain("An unexpected error occurred");
    }

    /**
     * Sends the request target byte for byte: java.net.URI, and so the Java HTTP clients, reject
     * an invalid percent-encoding before sending it. HTTP/1.0 keeps the response unchunked.
     */
    private String rawGet(String target) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + target + " HTTP/1.0\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            try (InputStream in = socket.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            DataJdbcRepositoriesAutoConfiguration.class,
            DataRedisAutoConfiguration.class
    })
    @Import({GlobalExceptionHandler.class, TestOnlyController.class})
    static class TestApplication {
    }

    @RestController
    static class TestOnlyController {

        @GetMapping("/test/model-attribute")
        String modelAttribute(@Valid @ModelAttribute CalculationRequest request) {
            return "ok";
        }

        @GetMapping("/test/request-param")
        String requestParam(@RequestParam(defaultValue = "0") int page) {
            return "ok";
        }
    }
}
