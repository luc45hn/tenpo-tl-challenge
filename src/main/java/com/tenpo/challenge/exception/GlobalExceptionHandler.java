package com.tenpo.challenge.exception;

import com.tenpo.challenge.filter.RequestPaths;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Translates every exception into an RFC 9457 problem detail: our own ones, the standard Spring
 * MVC ones (inherited from {@link ResponseEntityExceptionHandler}, with descriptive messages) and,
 * as a last resort, anything unexpected as a generic 500 that leaks no internals.
 * <p>
 * Logging: client errors (4xx) at DEBUG, the expected 503 at WARN on one line, unexpected server
 * errors at ERROR with the stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_NUMBER_MESSAGE = "must be a valid number";
    private static final String REQUIRED_MESSAGE = "is required";
    private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";

    /**
     * Handles invalid request parameters, whether they failed conversion (e.g. non-numeric)
     * or bean validation (e.g. missing). Only the first error per field is reported.
     */
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException ex) {
        return invalidParameters(fieldErrors(ex.getFieldErrors()));
    }

    /**
     * Same as {@link #handleBindException}, for the subclass Spring MVC raises when a
     * {@code @Valid @ModelAttribute} fails.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return handleExceptionInternal(ex, invalidParameters(fieldErrors(ex.getFieldErrors())), headers, status,
                request);
    }

    /**
     * Handles request parameters that violate their constraints (e.g. a page size above the
     * maximum). Only the first error per parameter is reported.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = ex.getParameterValidationResults().stream()
                .collect(Collectors.toMap(
                        result -> result.getMethodParameter().getParameterName(),
                        result -> result.getResolvableErrors().getFirst().getDefaultMessage(),
                        (first, ignored) -> first,
                        TreeMap::new));
        return handleExceptionInternal(ex, invalidParameters(errors), headers, status, request);
    }

    /**
     * Handles request parameters that cannot be converted to their type (e.g. a non-numeric page).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return invalidParameters(Map.of(ex.getName(), INVALID_NUMBER_MESSAGE));
    }

    /**
     * Handles a query string that Tomcat cannot decode (e.g. an invalid percent-encoding such as
     * {@code %zz}), raised when the parameters are first read. The parameter name and value are
     * not echoed: Tomcat warns they may be corrupted.
     */
    @ExceptionHandler(InvalidParameterException.class)
    public ProblemDetail handleMalformedQueryString(InvalidParameterException ex, HttpServletRequest request) {
        log.debug("Client error 400 handling {} {}: malformed query string", request.getMethod(),
                RequestPaths.pathWithinApplication(request));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The query string is malformed: a parameter contains an invalid percent-encoding.");
        problem.setTitle("Invalid request parameters");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        return handleExceptionInternal(ex, invalidParameters(Map.of(ex.getParameterName(), REQUIRED_MESSAGE)),
                headers, status, request);
    }

    /**
     * Handles failures to obtain the percentage from the external service: an expected outage,
     * logged on one line with its root cause.
     */
    @ExceptionHandler(PercentageUnavailableException.class)
    public ProblemDetail handlePercentageUnavailable(PercentageUnavailableException ex) {
        log.warn("Percentage service unavailable: {}", NestedExceptionUtils.getMostSpecificCause(ex).toString());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The percentage could not be obtained from the external service. Please try again later.");
        problem.setTitle("Percentage service unavailable");
        return problem;
    }

    /**
     * Handles requests over the rate limit, telling the client when to retry.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.debug("Rate limit exceeded: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Rate limit exceeded");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, notFound(status, request), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpHeaders headers,
                                                                   HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, notFound(status, request), headers, status, request);
    }

    /**
     * 405 naming the methods the path accepts; the Allow header comes with {@code headers}.
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers, HttpStatusCode status,
                                                                         WebRequest request) {
        String supported = Optional.ofNullable(ex.getSupportedHttpMethods())
                .filter(methods -> !methods.isEmpty())
                .map(methods -> " Supported methods: " + join(methods.stream().map(HttpMethod::name).toList()) + ".")
                .orElse("");
        String detail = "Method %s is not supported for %s.%s".formatted(ex.getMethod(), pathOf(request), supported);
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                      HttpHeaders headers, HttpStatusCode status,
                                                                      WebRequest request) {
        String detail = "None of the requested media types can be produced. Supported media types: %s."
                .formatted(join(mediaTypes(ex.getSupportedMediaTypes())));
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpHeaders headers, HttpStatusCode status,
                                                                     WebRequest request) {
        String detail = "Content type %s is not supported. Supported content types: %s.".formatted(
                Optional.ofNullable(ex.getContentType()).map(MediaType::toString).orElse("(none)"),
                join(mediaTypes(ex.getSupportedMediaTypes())));
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request body is missing or malformed.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Last resort for anything unexpected: a generic 500 that leaks no internals, logged once at
     * ERROR with the stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {} {}", request.getMethod(), RequestPaths.pathWithinApplication(request),
                ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE);
    }

    /**
     * Common exit of the inherited handlers: logs client errors at DEBUG, and turns server errors
     * into the generic 500 body (logged at ERROR with the stack trace) so they leak no internals.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Unexpected error handling {}", describe(request), ex);
            body = ProblemDetail.forStatusAndDetail(statusCode, UNEXPECTED_ERROR_MESSAGE);
        } else {
            log.debug("Client error {} handling {}: {}", statusCode.value(), describe(request), ex.getMessage());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ProblemDetail invalidParameters(Map<String, String> errors) {
        String detail = errors.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining("; "));
        log.debug("Invalid request parameters: {}", detail);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request parameters");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        GlobalExceptionHandler::messageOf,
                        (first, ignored) -> first,
                        TreeMap::new));
    }

    private static String messageOf(FieldError error) {
        return error.isBindingFailure() ? INVALID_NUMBER_MESSAGE : error.getDefaultMessage();
    }

    private static ProblemDetail notFound(HttpStatusCode status, WebRequest request) {
        return ProblemDetail.forStatusAndDetail(status, "No endpoint %s.".formatted(describe(request)));
    }

    private static List<String> mediaTypes(Collection<MediaType> mediaTypes) {
        return CollectionUtils.isEmpty(mediaTypes)
                ? List.of()
                : mediaTypes.stream().map(MediaType::toString).toList();
    }

    private static String join(Collection<String> values) {
        return values.isEmpty() ? "(none)" : String.join(", ", values);
    }

    /**
     * "METHOD /path" of the request, for messages and logs.
     */
    private static String describe(WebRequest request) {
        return servletRequest(request)
                .map(servlet -> servlet.getMethod() + " " + RequestPaths.pathWithinApplication(servlet))
                .orElseGet(() -> request.getDescription(false));
    }

    private static String pathOf(WebRequest request) {
        return servletRequest(request).map(RequestPaths::pathWithinApplication).orElseGet(() -> request.getDescription(false));
    }

    private static Optional<HttpServletRequest> servletRequest(WebRequest request) {
        return request instanceof NativeWebRequest nativeRequest
                ? Optional.ofNullable(nativeRequest.getNativeRequest(HttpServletRequest.class))
                : Optional.empty();
    }
}
