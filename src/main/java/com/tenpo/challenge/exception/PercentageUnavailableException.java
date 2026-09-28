package com.tenpo.challenge.exception;

/**
 * Raised when the percentage to apply cannot be obtained.
 */
public class PercentageUnavailableException extends RuntimeException {

    public PercentageUnavailableException(Throwable cause) {
        super("The percentage could not be obtained from the external service", cause);
    }
}
