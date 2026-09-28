package com.tenpo.challenge.client;

import io.vavr.control.Try;

import java.math.BigDecimal;

/**
 * Client of the external service that provides the percentage to apply.
 */
public interface PercentageClient {

    /**
     * Fetches the current percentage. Never throws: any failure (connection error, timeout,
     * non-2xx response, malformed body) is returned as a failed {@link Try}.
     */
    Try<BigDecimal> fetchPercentage();
}
