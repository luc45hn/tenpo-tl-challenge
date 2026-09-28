package com.tenpo.challenge.ratelimit;

import com.tenpo.challenge.filter.FilterOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    /**
     * Registered right after the call history filter, so rejected requests are recorded too.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiter rateLimiter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            RateLimitProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(rateLimiter, exceptionResolver, properties));
        registration.setOrder(FilterOrder.RATE_LIMIT);
        return registration;
    }
}
