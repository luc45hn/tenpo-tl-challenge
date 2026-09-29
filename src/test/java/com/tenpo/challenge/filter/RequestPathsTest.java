package com.tenpo.challenge.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPathsTest {

    @Test
    void returnsThePathWhenThereIsNoContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/calculate");

        assertThat(RequestPaths.pathWithinApplication(request)).isEqualTo("/api/v1/calculate");
    }

    @Test
    void removesTheContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/v1/history");
        request.setContextPath("/app");

        assertThat(RequestPaths.pathWithinApplication(request)).isEqualTo("/api/v1/history");
    }

    @Test
    void keepsThePathAsReceivedWithoutDecodingIt() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/some%20path");

        assertThat(RequestPaths.pathWithinApplication(request)).isEqualTo("/api/v1/some%20path");
    }
}
