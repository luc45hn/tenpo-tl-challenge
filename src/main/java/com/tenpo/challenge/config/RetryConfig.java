package com.tenpo.challenge.config;

import com.tenpo.challenge.client.TransientFailures;
import com.tenpo.challenge.retry.Pause;
import com.tenpo.challenge.retry.Retrier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PercentageRetryProperties.class)
public class RetryConfig {

    /**
     * Retries the external percentage call on transient failures only.
     */
    @Bean
    public Retrier percentageRetrier(PercentageRetryProperties properties) {
        return new Retrier(properties.maxAttempts(), TransientFailures::isTransient, properties.delay(), Pause.SLEEP);
    }
}
