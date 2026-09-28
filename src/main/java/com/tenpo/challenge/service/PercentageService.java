package com.tenpo.challenge.service;

import com.tenpo.challenge.client.PercentageClient;
import io.vavr.control.Try;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Resolves the percentage to apply. Holds the effects around the external call.
 */
@Service
public class PercentageService {

    private final PercentageClient percentageClient;

    public PercentageService(PercentageClient percentageClient) {
        this.percentageClient = percentageClient;
    }

    public Try<BigDecimal> getPercentage() {
        return percentageClient.fetchPercentage();
    }
}
