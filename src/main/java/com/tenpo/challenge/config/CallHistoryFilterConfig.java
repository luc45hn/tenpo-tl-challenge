package com.tenpo.challenge.config;

import com.tenpo.challenge.filter.CallHistoryFilter;
import com.tenpo.challenge.service.CallHistoryRecorder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(HistoryProperties.class)
public class CallHistoryFilterConfig {

    /**
     * Registered with the highest precedence so that it wraps every other filter (e.g. rate
     * limiting) and records the responses they produce too.
     */
    @Bean
    public FilterRegistrationBean<CallHistoryFilter> callHistoryFilter(CallHistoryRecorder recorder, Clock clock,
                                                                       HistoryProperties properties) {
        FilterRegistrationBean<CallHistoryFilter> registration =
                new FilterRegistrationBean<>(new CallHistoryFilter(recorder, clock, properties.maxBodyLength()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
