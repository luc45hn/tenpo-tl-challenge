package com.tenpo.challenge.filter;

import com.tenpo.challenge.config.CallHistoryFilterConfig;
import com.tenpo.challenge.config.ClockConfig;
import com.tenpo.challenge.config.HistoryExecutorConfig;
import com.tenpo.challenge.controller.CalculationController;
import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.CallHistoryRecorder;
import com.tenpo.challenge.service.PercentageService;
import com.tenpo.challenge.repository.CallHistoryRepository;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter, real async recorder and executor together, with a repository that blocks: the
 * response must not wait for the insert.
 */
@WebMvcTest(controllers = CalculationController.class)
@Import({CallHistoryFilterConfig.class, HistoryExecutorConfig.class, CallHistoryRecorder.class,
        CalculationService.class, ClockConfig.class})
@TestPropertySource(properties = {
        "history.max-body-length=10000",
        "history.executor.core-pool-size=1",
        "history.executor.max-pool-size=1",
        "history.executor.queue-capacity=10"
})
class CallHistoryAsyncRecordingTest {

    private static final long WAIT_SECONDS = 10;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CallHistoryRepository repository;

    @MockitoBean
    private PercentageService percentageService;

    @Test
    void aBlockingInsertDoesNotDelayTheResponse() throws InterruptedException {
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("10")));
        CountDownLatch releaseInsert = new CountDownLatch(1);
        CountDownLatch inserted = new CountDownLatch(1);
        willAnswer(invocation -> {
            releaseInsert.await(WAIT_SECONDS, TimeUnit.SECONDS);
            inserted.countDown();
            return invocation.getArgument(0);
        }).given(repository).save(any());

        // Were the insert synchronous, the request would block until releaseInsert times out
        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("{\"result\":11.00}")));
        assertThat(inserted.getCount()).as("insert still pending when the response completed").isEqualTo(1);

        releaseInsert.countDown();
        assertThat(inserted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
}
