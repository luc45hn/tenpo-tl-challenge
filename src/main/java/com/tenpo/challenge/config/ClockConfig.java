package com.tenpo.challenge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /**
     * Source of the current time, injectable so time-dependent logic can be tested.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
