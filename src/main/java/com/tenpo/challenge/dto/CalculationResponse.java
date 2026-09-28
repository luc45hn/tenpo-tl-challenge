package com.tenpo.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Result of the calculation endpoint.
 */
public record CalculationResponse(
        @Schema(description = "(num1 + num2) with the percentage applied, rounded to 2 decimals.", example = "11.00")
        BigDecimal result) {
}
