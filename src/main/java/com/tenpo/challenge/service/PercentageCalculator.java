package com.tenpo.challenge.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure arithmetic of the calculation endpoint: no state, no side effects.
 */
public final class PercentageCalculator {

    private static final int RESULT_SCALE = 2;

    private PercentageCalculator() {
    }

    /**
     * Returns {@code (num1 + num2) * (1 + percentage / 100)}, rounded to 2 decimals with
     * {@link RoundingMode#HALF_UP} only at the end. The division by 100 is an exact decimal
     * point shift, so no intermediate precision is lost.
     */
    public static BigDecimal applyPercentage(BigDecimal num1, BigDecimal num2, BigDecimal percentage) {
        BigDecimal factor = BigDecimal.ONE.add(percentage.movePointLeft(2));
        return num1.add(num2)
                .multiply(factor)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
