package com.tenpo.challenge.filter;

import com.tenpo.challenge.config.CallHistoryFilterConfig;
import com.tenpo.challenge.controller.CalculationController;
import com.tenpo.challenge.controller.MockPercentageController;
import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.CallHistoryRecorder;
import com.tenpo.challenge.service.PercentageService;
import io.vavr.control.Try;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CalculationController.class, MockPercentageController.class})
@Import({CallHistoryFilterConfig.class, CalculationService.class, CallHistoryFilterTest.FixedClockConfig.class})
@TestPropertySource(properties = "history.max-body-length=10000")
class CallHistoryFilterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CallHistoryRecorder recorder;

    @MockitoBean
    private PercentageService percentageService;

    @MockitoBean
    private RandomGenerator randomGenerator;

    @Test
    void recordsASuccessfulCalculation() throws Exception {
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("10")));

        mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"result\":11.00}"));

        assertThat(recordedCall()).isEqualTo(CallHistory.of(
                NOW, "GET", "/api/v1/calculate", "num1=5&num2=5", 200, "{\"result\":11.00}"));
    }

    @Test
    void recordsABadRequestWithItsProblemDetail() throws Exception {
        String body = mockMvc.perform(get("/api/v1/calculate?num1=abc&num2=5"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        CallHistory recorded = recordedCall();
        assertThat(recorded.status()).isEqualTo(400);
        assertThat(recorded.queryString()).isEqualTo("num1=abc&num2=5");
        assertThat(recorded.responseBody()).isEqualTo(body).contains("num1 must be a valid number");
    }

    @Test
    void recordsAServiceUnavailableWithItsProblemDetail() throws Exception {
        given(percentageService.getPercentage()).willReturn(Try.failure(new ResourceAccessException("down")));

        String body = mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        CallHistory recorded = recordedCall();
        assertThat(recorded.status()).isEqualTo(503);
        assertThat(recorded.responseBody()).isEqualTo(body).contains("Percentage service unavailable");
    }

    @Test
    void recordsAnUnhandledExceptionAsInternalServerError() {
        given(percentageService.getPercentage()).willThrow(new IllegalStateException("unexpected"));

        assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5")))
                .isInstanceOf(ServletException.class);

        CallHistory recorded = recordedCall();
        assertThat(recorded.status()).isEqualTo(500);
        assertThat(recorded.path()).isEqualTo("/api/v1/calculate");
    }

    @Test
    void doesNotRecordTheMockService() throws Exception {
        given(randomGenerator.nextInt(500, 2001)).willReturn(1234);

        mockMvc.perform(get("/mock/percentage")).andExpect(status().isOk());

        verifyNoInteractions(recorder);
    }

    @Test
    void doesNotRecordTheHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/history?page=0"));

        verifyNoInteractions(recorder);
    }

    @Test
    void aFailingRecorderDoesNotChangeTheResponse() throws Exception {
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("10")));
        willThrow(new IllegalStateException("recorder down")).given(recorder).record(any());

        mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"result\":11.00}"));
    }

    @Test
    void truncatesTheStoredBodyButDeliversTheFullBodyToTheClient() throws Exception {
        CallHistoryRecorder truncatingRecorder = mock(CallHistoryRecorder.class);
        CallHistoryFilter filter = new CallHistoryFilter(truncatingRecorder, Clock.fixed(NOW, ZoneOffset.UTC), 10);
        String fullBody = "{\"result\":\"" + "x".repeat(100) + "\"}";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/calculate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            res.setContentType("application/json");
            res.getOutputStream().write(fullBody.getBytes(StandardCharsets.UTF_8));
        });

        assertThat(response.getContentAsString()).isEqualTo(fullBody);
        ArgumentCaptor<CallHistory> captor = ArgumentCaptor.forClass(CallHistory.class);
        verify(truncatingRecorder).record(captor.capture());
        assertThat(captor.getValue().responseBody()).isEqualTo(fullBody.substring(0, 10));
    }

    @Test
    void recordsTheInstantTheCallStartedNotWhenItFinished() throws Exception {
        CallHistoryRecorder timingRecorder = mock(CallHistoryRecorder.class);
        AdvancingClock clock = new AdvancingClock(NOW);
        CallHistoryFilter filter = new CallHistoryFilter(timingRecorder, clock, 10000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/calculate");

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> clock.advance(Duration.ofSeconds(5)));

        ArgumentCaptor<CallHistory> captor = ArgumentCaptor.forClass(CallHistory.class);
        verify(timingRecorder).record(captor.capture());
        assertThat(clock.instant()).isEqualTo(NOW.plusSeconds(5));
        assertThat(captor.getValue().calledAt()).isEqualTo(NOW);
    }

    private CallHistory recordedCall() {
        ArgumentCaptor<CallHistory> captor = ArgumentCaptor.forClass(CallHistory.class);
        verify(recorder).record(captor.capture());
        return captor.getValue();
    }

    /**
     * A clock that only moves when told to.
     */
    private static final class AdvancingClock extends Clock {

        private Instant now;

        AdvancingClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
