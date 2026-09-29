package com.tenpo.challenge.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request path helpers shared by the servlet filters.
 */
public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * The request path without the context path, as received (not decoded), e.g.
     * {@code /api/v1/calculate}.
     */
    public static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
