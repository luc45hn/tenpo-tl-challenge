package com.tenpo.challenge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PercentageCacheProperties.class)
public class PercentageCacheConfig {
}
