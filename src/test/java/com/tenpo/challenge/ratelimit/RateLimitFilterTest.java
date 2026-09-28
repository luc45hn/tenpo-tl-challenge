package com.tenpo.challenge.ratelimit;

import com.tenpo.challenge.config.CallHistoryFilterConfig;
import com.tenpo.challenge.controller.CalculationController;
import com.tenpo.challenge.controller.MockPercentageController;
import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.CallHistoryRecorder;
import com.tenpo.challenge.service.PercentageService;
import io.vavr.control.Try;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rate limit filter with a fake limiter, inside the call history filter, as in the
 * application. The limit (3 per minute) comes from application.yml.
 */
@WebMvcTest(controllers = {CalculationController.class, MockPercentageController.class})
@Import({RateLimitConfig.class, CallHistoryFilterConfig.class, CalculationService.class,
        RateLimitFilterTest.TestBeans.class})
class RateLimitFilterTest {

    private static final String REJECTION_MESSAGE =
            "Rate limit exceeded: at most 3 requests per minute. Try again in 42 seconds.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeRateLimiter rateLimiter;

    @MockitoBean
    private PercentageService percentageService;

    @MockitoBean
    private RandomGenerator randomGenerator;

    @MockitoBean
    private CallHistoryRecorder recorder;

    @BeforeEach
    void allowByDefault() {
        rateLimiter.reset(RateLimitDecision.ALLOWED);
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("10")));
    }

    @Test
    void anAllowedRequestReachesTheController() throws Exception {
        mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(content().string("{\"result\":11.00}"));

        assertThat(rateLimiter.calls).isOne();
    }

    @Test
    void aRejectedRequestGetsTooManyRequestsWithoutReachingTheController() throws Exception {
        rateLimiter.reset(new RateLimitDecision.Rejected(Duration.ofSeconds(42)));

        mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.detail").value(REJECTION_MESSAGE))
                .andExpect(jsonPath("$.instance").value("/api/v1/calculate"));

        verify(percentageService, never()).getPercentage();
    }

    @Test
    void anInvalidRequestStillCountsAgainstTheLimit() throws Exception {
        mockMvc.perform(get("/api/v1/calculate?num1=abc&num2=5"))
                .andExpect(status().isBadRequest());

        assertThat(rateLimiter.calls).isOne();
    }

    @Test
    void theHistoryEndpointCountsAgainstTheLimit() throws Exception {
        mockMvc.perform(get("/api/v1/history"));

        assertThat(rateLimiter.calls).isOne();
    }

    @Test
    void theMockServiceIsNotCounted() throws Exception {
        given(randomGenerator.nextInt(500, 2001)).willReturn(1234);

        mockMvc.perform(get("/mock/percentage")).andExpect(status().isOk());

        assertThat(rateLimiter.calls).isZero();
    }

    @Test
    void pathsOutsideTheApiAreNotCounted() throws Exception {
        mockMvc.perform(get("/somewhere-else"));
        mockMvc.perform(get("/api/v2/calculate"));

        assertThat(rateLimiter.calls).isZero();
        verifyNoInteractions(recorder);
    }

    @Test
    void aRejectedRequestIsRecordedInTheHistory() throws Exception {
        rateLimiter.reset(new RateLimitDecision.Rejected(Duration.ofSeconds(42)));

        mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5")).andExpect(status().isTooManyRequests());

        ArgumentCaptor<CallHistory> captor = ArgumentCaptor.forClass(CallHistory.class);
        verify(recorder).record(captor.capture());
        CallHistory recorded = captor.getValue();
        assertThat(recorded.status()).isEqualTo(429);
        assertThat(recorded.path()).isEqualTo("/api/v1/calculate");
        assertThat(recorded.queryString()).isEqualTo("num1=5&num2=5");
        assertThat(recorded.responseBody()).contains(REJECTION_MESSAGE);
    }

    /**
     * Returns a configurable decision and counts the calls.
     */
    static final class FakeRateLimiter implements RateLimiter {

        private RateLimitDecision decision = RateLimitDecision.ALLOWED;
        private int calls;

        void reset(RateLimitDecision decision) {
            this.decision = decision;
            this.calls = 0;
        }

        @Override
        public RateLimitDecision tryAcquire() {
            calls++;
            return decision;
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        FakeRateLimiter rateLimiter() {
            return new FakeRateLimiter();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
