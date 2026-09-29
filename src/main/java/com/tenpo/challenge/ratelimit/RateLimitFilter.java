package com.tenpo.challenge.ratelimit;

import com.tenpo.challenge.exception.RateLimitExceededException;
import com.tenpo.challenge.filter.RequestPaths;
import com.tenpo.challenge.ratelimit.RateLimitDecision.Allowed;
import com.tenpo.challenge.ratelimit.RateLimitDecision.Rejected;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;

/**
 * Applies the global rate limit to every call under {@code /api/v1/**}. Rejected requests never
 * reach the controllers; their 429 response is produced by the MVC exception handling, so the
 * error format lives in one place.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final PathPattern LIMITED_PATHS = PathPatternParser.defaultInstance.parse("/api/v1/**");

    private final RateLimiter rateLimiter;
    private final HandlerExceptionResolver exceptionResolver;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiter rateLimiter, HandlerExceptionResolver exceptionResolver,
                           RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.exceptionResolver = exceptionResolver;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.matches(PathContainer.parsePath(RequestPaths.pathWithinApplication(request)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        switch (rateLimiter.tryAcquire()) {
            case Allowed allowed -> chain.doFilter(request, response);
            case Rejected rejected -> reject(request, response, rejected);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Rejected rejected)
            throws IOException {
        RateLimitExceededException exception = new RateLimitExceededException(
                properties.maxRequests(), properties.window(), rejected.retryAfter());
        if (exceptionResolver.resolveException(request, response, null, exception) == null) {
            // No handler resolved it: still answer 429 rather than letting the request through
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()));
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), exception.getMessage());
        }
    }
}
