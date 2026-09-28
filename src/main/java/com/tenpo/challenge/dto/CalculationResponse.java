package com.tenpo.challenge.dto;

import java.math.BigDecimal;

/**
 * Result of the calculation endpoint.
 */
public record CalculationResponse(BigDecimal result) {
}
