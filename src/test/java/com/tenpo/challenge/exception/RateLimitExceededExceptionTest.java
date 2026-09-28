package com.tenpo.challenge.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitExceededExceptionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "3  | PT1M   | 42   | Rate limit exceeded: at most 3 requests per minute. Try again in 42 seconds.",
            "1  | PT30S  | 1    | Rate limit exceeded: at most 1 request per 30 seconds. Try again in 1 second.",
            "5  | PT1H   | 3600 | Rate limit exceeded: at most 5 requests per hour. Try again in 3600 seconds.",
            "10 | PT90S  | 7    | Rate limit exceeded: at most 10 requests per 90 seconds. Try again in 7 seconds.",
            "2  | PT2M   | 61   | Rate limit exceeded: at most 2 requests per 2 minutes. Try again in 61 seconds.",
            "4  | P1D    | 5    | Rate limit exceeded: at most 4 requests per day. Try again in 5 seconds.",
            "2  | PT1.5S | 1    | Rate limit exceeded: at most 2 requests per 1500 milliseconds. Try again in 1 second."
    })
    void describesTheConfiguredLimitAndTheRetryTime(int maxRequests, Duration window, long retrySeconds,
                                                     String message) {
        RateLimitExceededException exception =
                new RateLimitExceededException(maxRequests, window, Duration.ofSeconds(retrySeconds));

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.retryAfterSeconds()).isEqualTo(retrySeconds);
    }
}
