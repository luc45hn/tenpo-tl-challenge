package com.tenpo.challenge.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Query parameters of the calculation endpoint.
 */
public record CalculationRequest(
        @NotNull(message = "is required") BigDecimal num1,
        @NotNull(message = "is required") BigDecimal num2) {
}
