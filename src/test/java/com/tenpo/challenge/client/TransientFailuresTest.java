package com.tenpo.challenge.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One case per category, mirroring the failures {@link RestPercentageClient} produces
 * (see {@link RestPercentageClientTest}).
 */
class TransientFailuresTest {

    @Test
    void connectionErrorsAreTransient() {
        ResourceAccessException failure =
                new ResourceAccessException("I/O error on GET request", new ConnectException());

        assertThat(TransientFailures.isTransient(failure)).isTrue();
    }

    @Test
    void timeoutsAreTransient() {
        ResourceAccessException failure =
                new ResourceAccessException("I/O error on GET request", new HttpTimeoutException("request timed out"));

        assertThat(TransientFailures.isTransient(failure)).isTrue();
    }

    @Test
    void serverErrorsAreTransient() {
        HttpServerErrorException failure = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(TransientFailures.isTransient(failure)).isTrue();
    }

    @Test
    void clientErrorsAreNotTransient() {
        HttpClientErrorException failure = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(TransientFailures.isTransient(failure)).isFalse();
    }

    @Test
    void malformedBodiesAreNotTransient() {
        RestClientException failure = new RestClientException("Error while extracting response",
                new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0])));

        assertThat(TransientFailures.isTransient(failure)).isFalse();
    }

    @Test
    void bodiesWithoutAPercentageAreNotTransient() {
        IllegalStateException failure =
                new IllegalStateException("Percentage service returned a response without a percentage");

        assertThat(TransientFailures.isTransient(failure)).isFalse();
    }
}
