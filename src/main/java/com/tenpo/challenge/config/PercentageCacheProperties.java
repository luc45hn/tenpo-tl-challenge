package com.tenpo.challenge.config;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the percentage cache.
 *
 * @param ttl how long a stored percentage is considered fresh
 */
@Validated
@ConfigurationProperties("percentage.cache")
public record PercentageCacheProperties(@NotNull @DurationMin(seconds = 1) Duration ttl) {
}
