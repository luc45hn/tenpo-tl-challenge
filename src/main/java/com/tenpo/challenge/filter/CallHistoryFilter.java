package com.tenpo.challenge.filter;

import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.service.CallHistoryRecorder;
import io.vavr.control.Try;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Records every call to {@code /api/v1/**} (except the history endpoint itself) with its
 * response, handing the record to {@link CallHistoryRecorder}. Recording is best effort: it never
 * changes nor breaks the response.
 */
public class CallHistoryFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CallHistoryFilter.class);

    private static final PathPattern RECORDED_PATHS = PathPatternParser.defaultInstance.parse("/api/v1/**");
    private static final PathPattern HISTORY_PATHS = PathPatternParser.defaultInstance.parse("/api/v1/history/**");

    private final CallHistoryRecorder recorder;
    private final Clock clock;
    private final int maxBodyLength;

    public CallHistoryFilter(CallHistoryRecorder recorder, Clock clock, int maxBodyLength) {
        this.recorder = recorder;
        this.clock = clock;
        this.maxBodyLength = maxBodyLength;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(pathOf(request));
        return !RECORDED_PATHS.matches(path) || HISTORY_PATHS.matches(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant calledAt = clock.instant();
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(request, wrappedResponse);
            completed = true;
        } finally {
            try {
                record(calledAt, request, wrappedResponse, completed);
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    /**
     * Builds the record and hands it off. A request that ended in an unhandled exception is
     * recorded as 500. Any failure is logged and swallowed.
     */
    private void record(Instant calledAt, HttpServletRequest request, ContentCachingResponseWrapper response,
                        boolean completed) {
        Try.run(() -> recorder.record(CallHistory.of(
                        calledAt,
                        request.getMethod(),
                        pathOf(request),
                        request.getQueryString(),
                        completed ? response.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        bodyOf(response))))
                .onFailure(e -> log.warn("Could not record call history for {} {}: {}",
                        request.getMethod(), request.getRequestURI(), e.toString()));
    }

    /**
     * The response body as text, truncated to {@code maxBodyLength} characters, or null if empty.
     */
    private String bodyOf(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        String body = new String(content, charsetOf(response));
        return body.length() <= maxBodyLength ? body : body.substring(0, truncationIndex(body));
    }

    /**
     * Cuts at {@code maxBodyLength} without splitting a surrogate pair.
     */
    private int truncationIndex(String body) {
        return Character.isHighSurrogate(body.charAt(maxBodyLength - 1)) ? maxBodyLength - 1 : maxBodyLength;
    }

    /**
     * The charset declared in the content type, or UTF-8 (the encoding of JSON) if none.
     */
    private static Charset charsetOf(HttpServletResponse response) {
        return Optional.ofNullable(response.getContentType())
                .map(MediaType::parseMediaType)
                .map(MediaType::getCharset)
                .orElse(StandardCharsets.UTF_8);
    }

    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
