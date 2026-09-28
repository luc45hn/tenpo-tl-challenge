package com.tenpo.challenge.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the global rate limit.
 *
 * @param maxRequests maximum number of requests accepted within the window
 * @param window      length of the sliding window
 */
@Validated
@ConfigurationProperties("ratelimit")
public record RateLimitProperties(
        @Min(1) int maxRequests,
        @NotNull @DurationMin(millis = 1) Duration window) {
}
