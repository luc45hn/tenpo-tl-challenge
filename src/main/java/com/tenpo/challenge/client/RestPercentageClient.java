package com.tenpo.challenge.client;

import com.tenpo.challenge.dto.PercentageResponse;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class RestPercentageClient implements PercentageClient {

    static final String PERCENTAGE_PATH = "/mock/percentage";

    private final RestClient restClient;

    public RestPercentageClient(@Qualifier("percentageRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Try<BigDecimal> fetchPercentage() {
        return Try.of(() -> restClient.get()
                        .uri(PERCENTAGE_PATH)
                        .retrieve()
                        .body(PercentageResponse.class))
                .flatMap(RestPercentageClient::extractPercentage);
    }

    private static Try<BigDecimal> extractPercentage(PercentageResponse response) {
        return Option.of(response)
                .flatMap(body -> Option.of(body.percentage()))
                .toTry(() -> new IllegalStateException(
                        "Percentage service returned a response without a percentage"));
    }
}
