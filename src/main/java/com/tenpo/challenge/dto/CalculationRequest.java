package com.tenpo.challenge.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Query parameters of the calculation endpoint.
 */
public record CalculationRequest(
        @Parameter(description = "First number to add.", example = "5")
        @NotNull(message = "is required") BigDecimal num1,
        @Parameter(description = "Second number to add.", example = "5")
        @NotNull(message = "is required") BigDecimal num2) {
}
