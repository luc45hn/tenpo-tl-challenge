package com.tenpo.challenge.filter;

import org.springframework.core.Ordered;

/**
 * Order of the application's servlet filters, lowest first.
 */
public final class FilterOrder {

    /**
     * Outermost, so it records the responses produced by every filter inside it.
     */
    public static final int CALL_HISTORY = Ordered.HIGHEST_PRECEDENCE;

    /**
     * Right inside the call history filter, so rejected requests (429) are recorded too.
     */
    public static final int RATE_LIMIT = CALL_HISTORY + 1;

    private FilterOrder() {
    }
}
