package com.tenpo.challenge.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DurationsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "PT30M   | 30 minutes",
            "PT1M    | 1 minute",
            "PT90S   | 90 seconds",
            "PT1S    | 1 second",
            "PT2H    | 2 hours",
            "PT1H    | 1 hour",
            "P1D     | 1 day",
            "P2D     | 2 days",
            "PT0.2S  | 200 milliseconds",
            "PT1.5S  | 1500 milliseconds",
            "PT0.001S | 1 millisecond"
    })
    void formatsTheQuantityInItsLargestWholeUnit(Duration duration, String expected) {
        assertThat(Durations.quantity(duration)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "per {0} -> per {1}")
    @CsvSource(delimiter = '|', value = {
            "PT1M   | minute",
            "PT1H   | hour",
            "P1D    | day",
            "PT1S   | second",
            "PT30S  | 30 seconds",
            "PT2M   | 2 minutes",
            "PT90S  | 90 seconds",
            "PT1.5S | 1500 milliseconds"
    })
    void formatsTheFormUsedAfterPer(Duration duration, String expected) {
        assertThat(Durations.per(duration)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PT0.0000005S", "PT1.0000001S"})
    void fallsBackToIsoForDurationsThatAreNotWholeMilliseconds(Duration duration) {
        assertThat(Durations.quantity(duration)).isEqualTo(duration.toString());
    }
}
