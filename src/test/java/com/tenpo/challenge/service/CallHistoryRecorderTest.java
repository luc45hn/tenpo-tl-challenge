package com.tenpo.challenge.service;

import com.tenpo.challenge.config.HistoryExecutorConfig;
import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.repository.CallHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs the recorder with its real async executor (one thread, a queue of one) against a mocked
 * repository. Latches make the waits deterministic.
 */
@SpringJUnitConfig({HistoryExecutorConfig.class, CallHistoryRecorder.class})
@TestPropertySource(properties = {
        "history.max-body-length=100",
        "history.executor.core-pool-size=1",
        "history.executor.max-pool-size=1",
        "history.executor.queue-capacity=1"
})
class CallHistoryRecorderTest {

    private static final long WAIT_SECONDS = 5;

    @Autowired
    private CallHistoryRecorder recorder;

    @MockitoBean
    private CallHistoryRepository repository;

    @Test
    void savesTheRecordOnTheHistoryExecutor() throws InterruptedException {
        CallHistory call = call("saved");
        CountDownLatch saved = new CountDownLatch(1);
        AtomicReference<String> savingThread = new AtomicReference<>();
        willAnswer(invocation -> {
            savingThread.set(Thread.currentThread().getName());
            saved.countDown();
            return invocation.getArgument(0);
        }).given(repository).save(any());

        recorder.record(call);

        assertThat(saved.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        verify(repository).save(call);
        assertThat(savingThread.get()).startsWith("history-");
    }

    @Test
    void dropsTheRecordWithoutBlockingOrThrowingWhenTheQueueIsFull() throws InterruptedException {
        CallHistory inProgress = call("in progress");
        CallHistory queued = call("queued");
        CallHistory dropped = call("dropped");
        CountDownLatch firstSaveStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSave = new CountDownLatch(1);
        CountDownLatch bothSaved = new CountDownLatch(2);
        willAnswer(invocation -> {
            firstSaveStarted.countDown();
            releaseFirstSave.await(WAIT_SECONDS, TimeUnit.SECONDS);
            bothSaved.countDown();
            return invocation.getArgument(0);
        }).given(repository).save(any());

        recorder.record(inProgress);
        assertThat(firstSaveStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        recorder.record(queued);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThatNoException()
                .isThrownBy(() -> recorder.record(dropped)));

        releaseFirstSave.countDown();

        assertThat(bothSaved.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        verify(repository).save(inProgress);
        verify(repository).save(queued);
        verify(repository, never()).save(dropped);
    }

    @Test
    void swallowsRepositoryFailuresAndKeepsRecording() throws InterruptedException {
        CountDownLatch attempts = new CountDownLatch(2);
        willAnswer(invocation -> {
            attempts.countDown();
            throw new IllegalStateException("database down");
        }).given(repository).save(any());

        recorder.record(call("first"));
        recorder.record(call("second"));

        assertThat(attempts.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        verify(repository, times(2)).save(any());
    }

    @Test
    void recordDoesNotThrowWhenTheRepositoryFails() {
        CallHistoryRepository failingRepository = mock(CallHistoryRepository.class);
        given(failingRepository.save(any())).willThrow(new IllegalStateException("database down"));

        assertThatNoException().isThrownBy(() -> new CallHistoryRecorder(failingRepository).record(call("direct")));
    }

    private static CallHistory call(String body) {
        return CallHistory.of(Instant.parse("2026-01-01T12:00:00Z"), "GET", "/api/v1/calculate", null, 200, body);
    }
}
