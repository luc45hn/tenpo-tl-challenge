package com.tenpo.challenge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;
import java.util.random.RandomGenerator;

@Configuration
public class RandomConfig {

    /**
     * Source of randomness for the percentage mock, injectable so tests can be deterministic.
     * {@link Random} is used because, unlike most {@link RandomGenerator} implementations,
     * it is thread-safe.
     */
    @Bean
    public RandomGenerator randomGenerator() {
        return new Random();
    }
}
