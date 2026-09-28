package com.tenpo.challenge.dto;

import java.math.BigDecimal;

/**
 * Body returned by the external percentage service.
 */
public record PercentageResponse(BigDecimal percentage) {
}
