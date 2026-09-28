package com.tenpo.challenge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the call history recording.
 *
 * @param maxBodyLength maximum number of characters of the response body that are stored
 * @param executor      thread pool that performs the inserts
 */
@Validated
@ConfigurationProperties("history")
public record HistoryProperties(@Min(1) int maxBodyLength, @NotNull @Valid Executor executor) {

    /**
     * @param corePoolSize  threads kept alive
     * @param maxPoolSize   maximum threads, used only once the queue is full
     * @param queueCapacity pending records beyond which new records are dropped
     */
    public record Executor(@Min(1) int corePoolSize, @Min(1) int maxPoolSize, @Min(1) int queueCapacity) {

        @AssertTrue(message = "max-pool-size must be greater than or equal to core-pool-size")
        public boolean isPoolSizeRangeValid() {
            return maxPoolSize >= corePoolSize;
        }
    }
}
