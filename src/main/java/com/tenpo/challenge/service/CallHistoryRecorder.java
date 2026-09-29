package com.tenpo.challenge.service;

import com.tenpo.challenge.config.HistoryExecutorConfig;
import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.repository.CallHistoryRepository;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.tenpo.challenge.exception.Failures.describe;

/**
 * Persists call history records asynchronously and best effort.
 */
@Service
public class CallHistoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(CallHistoryRecorder.class);

    private final CallHistoryRepository repository;

    public CallHistoryRecorder(CallHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Inserts the record on the history executor, so the caller returns immediately. Failures are
     * logged and swallowed.
     */
    @Async(HistoryExecutorConfig.HISTORY_EXECUTOR)
    public void record(CallHistory call) {
        Try.run(() -> repository.save(call))
                .onFailure(e -> log.warn("Could not record call history for {} {}: {}",
                        call.method(), call.path(), describe(e)));
    }
}
