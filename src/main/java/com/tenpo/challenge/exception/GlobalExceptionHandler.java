package com.tenpo.challenge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Translates exceptions into RFC 9457 problem details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_NUMBER_MESSAGE = "must be a valid number";

    /**
     * Handles invalid request parameters, whether they failed conversion (e.g. non-numeric)
     * or bean validation (e.g. missing). Only the first error per field is reported.
     */
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException ex) {
        Map<String, String> errors = ex.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        GlobalExceptionHandler::messageOf,
                        (first, ignored) -> first,
                        TreeMap::new));

        String detail = errors.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request parameters");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Handles failures to obtain the percentage from the external service.
     */
    @ExceptionHandler(PercentageUnavailableException.class)
    public ProblemDetail handlePercentageUnavailable(PercentageUnavailableException ex) {
        log.warn("Percentage service unavailable", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The percentage could not be obtained from the external service. Please try again later.");
        problem.setTitle("Percentage service unavailable");
        return problem;
    }

    private static String messageOf(FieldError error) {
        return error.isBindingFailure() ? INVALID_NUMBER_MESSAGE : error.getDefaultMessage();
    }
}
