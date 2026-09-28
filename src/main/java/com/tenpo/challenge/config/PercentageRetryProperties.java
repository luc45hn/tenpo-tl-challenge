package com.tenpo.challenge.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Retry settings of the external percentage call.
 *
 * @param maxAttempts total number of attempts, including the first call
 * @param delay       pause between attempts
 */
@Validated
@ConfigurationProperties("percentage.retry")
public record PercentageRetryProperties(
        @Min(1) @Max(10) int maxAttempts,
        @NotNull @DurationMin(millis = 0) @DurationMax(seconds = 5) Duration delay) {
}
