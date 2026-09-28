package com.tenpo.challenge.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * A recorded call to the public API.
 *
 * @param id           database identity, null until persisted
 * @param calledAt     when the call was made (UTC)
 * @param method       HTTP method
 * @param path         request path
 * @param queryString  raw query string, null if there was none
 * @param status       HTTP status of the response
 * @param responseBody response body (result or problem detail), possibly truncated; null if empty
 */
@Table("call_history")
public record CallHistory(
        @Id Long id,
        Instant calledAt,
        String method,
        String path,
        String queryString,
        int status,
        String responseBody) {

    /**
     * Creates a record that has not been persisted yet.
     */
    public static CallHistory of(Instant calledAt, String method, String path, String queryString, int status,
                                 String responseBody) {
        return new CallHistory(null, calledAt, method, path, queryString, status, responseBody);
    }

    /**
     * Returns a copy with the given id, used by Spring Data JDBC after inserting.
     */
    public CallHistory withId(Long id) {
        return new CallHistory(id, calledAt, method, path, queryString, status, responseBody);
    }
}
