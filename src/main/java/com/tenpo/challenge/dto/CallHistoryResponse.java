package com.tenpo.challenge.dto;

import com.tenpo.challenge.model.CallHistory;

import java.time.Instant;

/**
 * A recorded call, as returned by the history endpoint.
 */
public record CallHistoryResponse(
        Long id,
        Instant timestamp,
        String method,
        String path,
        String queryString,
        int status,
        String responseBody) {

    public static CallHistoryResponse from(CallHistory call) {
        return new CallHistoryResponse(call.id(), call.calledAt(), call.method(), call.path(), call.queryString(),
                call.status(), call.responseBody());
    }
}
